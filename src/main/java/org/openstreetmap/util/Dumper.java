package org.openstreetmap.util;

import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.openstreetmap.util.exceptions.DatabaseException;
import org.openstreetmap.util.exceptions.NoUsersFoundException;
import org.openstreetmap.util.exceptions.XMLException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;


public class Dumper {
  private static final int FETCH_SIZE = 10000;
  private static final XMLOutputFactory2 xmlof = (XMLOutputFactory2) XMLOutputFactory2.newInstance();
  private static Pattern sanitizePattern = Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]");
  private static final String NEW_LINE = System.getProperty("line.separator");  

  static {
    xmlof.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.FALSE);
    xmlof.setProperty(XMLOutputFactory2.XSP_NAMESPACE_AWARE, Boolean.FALSE);

    /*
    * Robustness is the slowest of all possible modes but I think in this case a few extra hours (haven't checked)
    * are worth the extra 'robustness'
    */
    //xmlof.configureForSpeed();
    //xmlof.configureForXmlConformance();
    xmlof.configureForRobustness();
  }

  private Connection connection;
  private XMLStreamWriter2 xmlw;

  private Map<Integer, String> users; // This might one day grow from Integer to Long but not for a long [sic!] time.

  private FileWriter fileListFile;
  private File gpxOutputFolder;

  public Dumper(String connectionUrl, File metadataFile, File fileListFile, File gpxOutputFolder) throws XMLException, DatabaseException, IOException {
    this.xmlw = createXMLWriter(new FileOutputStream(metadataFile));
    createDatabaseConnection(connectionUrl);
    this.fileListFile = new FileWriter(fileListFile);
    this.gpxOutputFolder = gpxOutputFolder;
  }

  private void createDatabaseConnection(String connectionUrl) throws DatabaseException {
    try {
      Class.forName("org.postgresql.Driver");
    } catch (ClassNotFoundException e) {
      throw new DatabaseException(
              "PostgreSQL JDBC driver (postgresql-X.X-XXX.jdbc4.jar) could not be found. Please add it to the CLASSPATH",
              e);
    }

    try {
      this.connection = DriverManager.getConnection(connectionUrl);
      this.connection.setAutoCommit(false);
      this.connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
    } catch (SQLException e) {
      throw new DatabaseException("Something has gone wrong creating a connection to the Database", e);
    }
  }

  private XMLStreamWriter2 createXMLWriter(OutputStream stream) throws XMLException {
    try {
      return (XMLStreamWriter2) xmlof.createXMLStreamWriter(stream);
    } catch (XMLStreamException e) {
      throw new XMLException("Error instantiating the XML Writer", e);
    }
  }

  public void dump() throws DatabaseException, XMLException, IOException {
    final Date timestamp = new Date();
    try {
      fetchUsers();
      writeOSMHeader(timestamp);

      writeIdentifiableAndPublicTraces();
      writeTrackableTraces();
      writePrivateTraces();

      writeOSMFooter();
      xmlw.flush();
    } catch (SQLException e) {
      throw new DatabaseException(e);
    } catch (XMLStreamException e) {
      throw new XMLException(e);
    } finally {
      try {
        xmlw.close();   // or xmlw.closeCompletely(); ?
        connection.rollback();
        connection.close();
      } catch (SQLException e) {
        /* Throwing in a finally block...I know but there is nothing that can really go wrong if the connection is not closed. */
        throw new DatabaseException("Something went wrong rolling back the transaction and closing the connection.", e);
      } catch (XMLStreamException e) {
        /* This on the other hand... */
        throw new XMLException("Problem closing the XML Writer.", e);
      }
    }
  }

  private ResultSet executeQuery(String sql) throws SQLException {
    Statement stmt = connection.createStatement();
    stmt.setFetchSize(FETCH_SIZE);
    return stmt.executeQuery(sql);
  }

  private PreparedStatement getPreparedStatement(String sql) throws SQLException {
    PreparedStatement stmt = connection.prepareStatement(sql);
    stmt.setFetchSize(FETCH_SIZE);
    return stmt;
  }

  private void writeOSMHeader(Date timestamp) throws XMLStreamException {
    xmlw.writeStartDocument();
    xmlw.writeStartElement("gpxFiles");
    xmlw.writeAttribute("generator", "OpenStreetMap PlanetGpxDump.java");
    xmlw.writeAttribute("timestamp", OSMUtils.dateFormat.format(timestamp));
  }

  private void writeOSMFooter() throws XMLStreamException {
    xmlw.writeEndElement(); // </osm>
  }

  private void fetchUsers() throws NoUsersFoundException, SQLException {
    ResultSet rs = null;
    try {
      /*
      This should not strictly be necessary (as everything is running in a single transaction)
      but it is the way it is done in users.cpp from the C version and it allows us to size the
      HashMap. Makes no real difference anyway.
      */
      rs = executeQuery("SELECT MAX(id) FROM users");
      if (!rs.next()) {
        throw new NoUsersFoundException();
      }

      int maxUserId = rs.getInt(1);
      rs.close();
      this.users = new HashMap<Integer, String>(maxUserId);

      PreparedStatement pstmt = getPreparedStatement("SELECT id, display_name FROM users WHERE id <= ?");
      pstmt.setInt(1, maxUserId);
      rs = pstmt.executeQuery();

      while (rs.next()) {
        users.put(rs.getInt(1), rs.getString(2));
      }
    } finally {
      if (rs != null) {
        rs.close();
      }
    }

  }

  private void writeIdentifiableAndPublicTraces() throws XMLStreamException, SQLException, IOException {
    ResultSet gpxFiles = null;
    ResultSet tags = null;

    try {
      gpxFiles = executeQuery(
              "SELECT id, user_id, timestamp, name, description, size, latitude, longitude, visibility " +
                      "FROM gpx_files " +
                      "WHERE inserted = true AND visible = true AND (visibility = 'identifiable' OR visibility = 'public') " +
                      "ORDER BY id");

      tags = executeQuery("SELECT gpx_id, tag FROM gpx_file_tags ORDER BY gpx_id, tag");
      tags.next();

      while (gpxFiles.next()) {
        xmlw.writeStartElement("gpxFile");
        xmlw.writeAttribute("id", gpxFiles.getString(1));
        xmlw.writeAttribute("timestamp", OSMUtils.dateFormat.format(gpxFiles.getTimestamp(3)));
        xmlw.writeAttribute("fileName", gpxFiles.getString(4));
        xmlw.writeAttribute("description", sanitize(gpxFiles.getString(5)));
        xmlw.writeAttribute("points", gpxFiles.getString(6));
        xmlw.writeAttribute("startLatitude",
                OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(7))));
        xmlw.writeAttribute("startLongitude",
                OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(8))));
        xmlw.writeAttribute("visibility", gpxFiles.getString(9));

        if (gpxFiles.getString(9).equals("identifiable")) {
          int uid = gpxFiles.getInt(2);
          if (users.containsKey(uid)) {
            xmlw.writeAttribute("user", users.get(uid));
            xmlw.writeAttribute("uid", gpxFiles.getString(2));
          }
        }

        writeGpxFileTags(tags, gpxFiles.getInt(1));

        xmlw.writeEndElement();

        fileListFile.write(gpxFiles.getString(4));
        fileListFile.write(NEW_LINE);        
      }
    } finally {
      if (gpxFiles != null) gpxFiles.close();
      if (tags != null) tags.close();
    }
  }

  private void writeGpxFileTags(ResultSet tags, int id) throws SQLException, XMLStreamException {
    if (tags.isAfterLast()) return;

    /*
     * Fast forward to the current trace Id
     */
    while (tags.getInt(1) < id) {
      if (!tags.next()) {
        return;
      }
    }

    // No tags for the current element?
    if (tags.getInt(1) != id) {
      return;
    }

    while (tags.getInt(1) == id) {
      xmlw.writeStartElement("tag");
      xmlw.writeCharacters(sanitize(tags.getString(2))); // I don't think we need to use CDATA here
      xmlw.writeEndElement();
      if (!tags.next()) {
        return;
      }
    }
  }  

  private void writeTrackableTraces() throws SQLException, XMLStreamException, IOException, XMLException {
    ResultSet gpxFiles = null;
    PreparedStatement gpxPointsStatement = getPreparedStatement("SELECT latitude, longitude, altitude, trackid, timestamp FROM gps_points WHERE gpx_id = ? ORDER by trackid, timestamp");
    ResultSet tags = null;

    try {
      gpxFiles = executeQuery(
              "SELECT id, timestamp, name, size, latitude, longitude, visibility " +
                      "FROM gpx_files " +
                      "WHERE inserted = true AND visible = true AND visibility = 'trackable'" +
                      "ORDER BY id");
/*
<gpxFile id="12347" timestamp="2006-03-21T09:33:03Z"
points="2143" startLatitude="10.1234567" startLongitude="53.1234567"
visibility="trackable"/> & no Tags or Description
 */
      while (gpxFiles.next()) {
        xmlw.writeStartElement("gpxFile");
        xmlw.writeAttribute("id", gpxFiles.getString(1));
        xmlw.writeAttribute("timestamp", OSMUtils.dateFormat.format(gpxFiles.getTimestamp(2)));
        xmlw.writeAttribute("fileName", gpxFiles.getString(3));
        xmlw.writeAttribute("points", gpxFiles.getString(4));
        xmlw.writeAttribute("startLatitude",
                OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(5))));
        xmlw.writeAttribute("startLongitude",
                OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(6))));
        xmlw.writeAttribute("visibility", gpxFiles.getString(7));

        xmlw.writeEndElement();

        fileListFile.write(gpxFiles.getString(3));
        fileListFile.write(NEW_LINE);

        // Write a custom GPX file with all the points for this trace
        gpxPointsStatement.setLong(1, gpxFiles.getLong(1));
        ResultSet gpxPoints = gpxPointsStatement.executeQuery();
        File outputFile = new File(gpxOutputFolder, gpxFiles.getString(3));
        //new GZIPOutputStream(new FileOutputStream(outputFile));
        XMLStreamWriter2 writer = createXMLWriter(new FileOutputStream(outputFile));
        writeGpxFile(writer, gpxPoints);
        writer.closeCompletely();
      }
    } finally {
      if (gpxFiles != null) gpxFiles.close();
      if (tags != null) tags.close();
    }
  }

  private void writePrivateTraces() throws SQLException, XMLStreamException, IOException {

  }

  private void writeGpxFile(XMLStreamWriter2 writer, ResultSet gpxPoints) throws XMLStreamException, SQLException {
    writer.writeStartDocument();
    writer.writeStartElement("gpx");
    writer.writeDefaultNamespace("http://www.topografix.com/GPX/1/1");
    writer.writeNamespace("xsi","http://www.w3.org/2001/XMLSchema-instance");
    //writer.writeAttribute schemaLocation
    writer.writeAttribute("version", "1.1");
    writer.writeAttribute("creator", "planet.gpx exporter");

    Integer trackId = null;

    while (gpxPoints.next()) {
      if (trackId == null) {
        trackId = gpxPoints.getInt(4);
        writeTrackElement(writer, trackId);        
      } else if (gpxPoints.getInt(4) != trackId) {
        writer.writeEndElement(); // </trk>
        trackId = gpxPoints.getInt(4);
        writeTrackElement(writer, trackId);
      }

      writer.writeStartElement("trkpt");

      writer.writeAttribute("lat", OSMUtils.convertCoordinateToString(gpxPoints.getInt(1)));
      writer.writeAttribute("lon", OSMUtils.convertCoordinateToString(gpxPoints.getInt(2)));

      gpxPoints.getDouble(3);
      if (!gpxPoints.wasNull()) {
        writer.writeStartElement("ele");
        writer.writeCharacters(OSMUtils.numberFormat.format(gpxPoints.getDouble(3)));
        writer.writeEndElement();
      }

      gpxPoints.getTimestamp(5);
      if (!gpxPoints.wasNull()) {
        writer.writeStartElement("time");
        writer.writeCharacters(OSMUtils.dateFormat.format(gpxPoints.getTimestamp(5)));
        writer.writeEndElement();
      }

      writer.writeEndElement();
    }

    writer.writeEndElement(); // </gpx>
  }

  private void writeTrackElement(XMLStreamWriter2 writer, Integer trackId) throws XMLStreamException {
        writer.writeStartElement("trk");

        writer.writeStartElement("number");
        writer.writeCharacters(trackId.toString());
        writer.writeEndElement();
  }

  private String sanitize(String s) {
    return sanitizePattern.matcher(s).replaceAll("");
  }

}

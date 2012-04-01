package org.openstreetmap.util;

import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.openstreetmap.util.exceptions.DatabaseException;
import org.openstreetmap.util.exceptions.NoUsersFoundException;
import org.openstreetmap.util.exceptions.XMLException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;


public class Dumper {
    private static final int FETCH_SIZE = 10000;
    private static final XMLOutputFactory2 XMLOF = (XMLOutputFactory2) XMLOutputFactory2.newInstance();
    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]");
    private static final String NEW_LINE = System.getProperty("line.separator");

    static {
        XMLOF.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.FALSE);
        XMLOF.setProperty(XMLOutputFactory2.XSP_NAMESPACE_AWARE, Boolean.TRUE);

        /*
         * Robustness is the slowest of all possible modes but I think in this
         * case a few extra hours (haven't checked) are worth the extra
         * 'robustness'
         */
        // XMLOF.configureForSpeed();
        // XMLOF.configureForXmlConformance();
        XMLOF.configureForRobustness();
    }

    private Connection connection;
    private final XMLStreamWriter2 xmlw;

    private Map<Integer, String> users;

    private final FileWriter fileListFile;
    private final File gpxOutputFolder;

    private final String gpxFolder;

    public Dumper(String connectionUrl, File metadataFile, File fileListFile, File gpxOutputFolder, String gpxFolder)
            throws XMLException, DatabaseException, IOException {
        xmlw = createXMLWriter(new FileOutputStream(metadataFile));
        createDatabaseConnection(connectionUrl);
        this.fileListFile = new FileWriter(fileListFile);
        this.gpxOutputFolder = gpxOutputFolder;
        this.gpxFolder = gpxFolder;
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
            connection = DriverManager.getConnection(connectionUrl);
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        } catch (SQLException e) {
            throw new DatabaseException("Something has gone wrong creating a connection to the Database", e);
        }
    }

    private XMLStreamWriter2 createXMLWriter(OutputStream stream) throws XMLException {
        try {
            return (XMLStreamWriter2) XMLOF.createXMLStreamWriter(stream);
        } catch (XMLStreamException e) {
            throw new XMLException("Error instantiating the XML Writer", e);
        }
    }

    public void dump() throws DatabaseException, XMLException, IOException {
        Date timestamp = new Date();
        try {
            fetchUsers();
            writeGPXHeader(timestamp);

            writePublicTraces();
            writeIdentifiableTraces();
            writeTrackableTraces();
            writePrivateTraces();

            writeGPXFooter();
            xmlw.flush();
        } catch (SQLException e) {
            throw new DatabaseException(e);
        } catch (XMLStreamException e) {
            throw new XMLException(e);
        } finally {
            try {
                xmlw.close(); // or xmlw.closeCompletely(); ?
                connection.rollback();
                connection.close();
                fileListFile.close();
            } catch (SQLException e) {
                /*
                 * Throwing in a finally block...I know but there is nothing
                 * that can really go wrong if the connection is not closed.
                 */
                throw new DatabaseException(
                        "Something went wrong rolling back the transaction and closing the connection.", e);
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

    private void writeGPXHeader(Date timestamp) throws XMLStreamException {
        xmlw.writeStartDocument();
        xmlw.writeStartElement("gpxFiles");
        xmlw.writeAttribute("generator", "OpenStreetMap PlanetGpxDump.java");
        xmlw.writeAttribute("timestamp", OSMUtils.dateFormat.format(timestamp));
    }

    private void writeGPXFooter() throws XMLStreamException {
        xmlw.writeEndElement(); // </gpxFiles>
    }

    private void fetchUsers() throws NoUsersFoundException, SQLException {
        ResultSet rs = null;
        try {
            /*
             * This should not strictly be necessary (as everything is running
             * in a single transaction) but it is the way it is done in
             * users.cpp from the C version and it allows us to size the
             * HashMap. Makes no real difference anyway.
             */
            rs = executeQuery("SELECT MAX(id) FROM users");
            if (!rs.next()) {
                throw new NoUsersFoundException();
            }

            int maxUserId = rs.getInt(1);
            rs.close();
            users = new HashMap<Integer, String>(maxUserId);

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

    private void writePublicTraces() throws XMLStreamException, SQLException, IOException {
        ResultSet gpxFiles = null;
        ResultSet tags = null;

        try {
            gpxFiles = executeQuery(
                    "SELECT id, user_id, timestamp, name, description, size, latitude, longitude, visibility "
                    + "FROM gpx_files "
                    + "WHERE inserted = true AND visible = true AND (visibility = 'public') "
                    + "ORDER BY id");

            while (gpxFiles.next()) {
                xmlw.writeStartElement("gpxFile");
                xmlw.writeAttribute("id", gpxFiles.getString(1));
                xmlw.writeAttribute("timestamp", OSMUtils.dateFormat.format(gpxFiles.getTimestamp(3)));
                xmlw.writeAttribute("fileName", Long.toString(gpxFiles.getLong(1)) + ".gpx");
                xmlw.writeAttribute("originalFileName", gpxFiles.getString(4));
                xmlw.writeAttribute("description", sanitize(gpxFiles.getString(5)));
                xmlw.writeAttribute("points", gpxFiles.getString(6));
                xmlw.writeAttribute("startLatitude", OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(7))));
                xmlw.writeAttribute("startLongitude", OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(8))));
                xmlw.writeAttribute("visibility", gpxFiles.getString(9));

                writeGpxFileTags(gpxFiles.getInt(1));

                xmlw.writeEndElement();

                appendGpxFileToExportList(new File(gpxFolder, gpxFiles.getString(1)));
            }
        } finally {
            if (gpxFiles != null)
                gpxFiles.close();
            if (tags != null)
                tags.close();
        }
    }

    private void writeIdentifiableTraces() throws XMLStreamException, SQLException, IOException {
        ResultSet gpxFiles = null;
        ResultSet tags = null;

        try {
            gpxFiles = executeQuery(
                    "SELECT id, user_id, timestamp, name, description, size, latitude, longitude, visibility "
                    + "FROM gpx_files "
                    + "WHERE inserted = true AND visible = true AND (visibility = 'identifiable') "
                    + "ORDER BY id");

            while (gpxFiles.next()) {
                xmlw.writeStartElement("gpxFile");
                xmlw.writeAttribute("id", gpxFiles.getString(1));
                xmlw.writeAttribute("timestamp", OSMUtils.dateFormat.format(gpxFiles.getTimestamp(3)));
                xmlw.writeAttribute("fileName", Long.toString(gpxFiles.getLong(1)) + ".gpx");
                xmlw.writeAttribute("originalFileName", gpxFiles.getString(4));
                xmlw.writeAttribute("description", sanitize(gpxFiles.getString(5)));
                xmlw.writeAttribute("points", gpxFiles.getString(6));
                xmlw.writeAttribute("startLatitude", OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(7))));
                xmlw.writeAttribute("startLongitude", OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(8))));
                xmlw.writeAttribute("visibility", gpxFiles.getString(9));

                int uid = gpxFiles.getInt(2);
                if (users.containsKey(uid)) {
                    xmlw.writeAttribute("user", users.get(uid));
                    xmlw.writeAttribute("uid", gpxFiles.getString(2));
                }

                writeGpxFileTags(gpxFiles.getInt(1));

                xmlw.writeEndElement();

                appendGpxFileToExportList(new File(gpxFolder, gpxFiles.getString(1)));
            }
        } finally {
            if (gpxFiles != null)
                gpxFiles.close();
            if (tags != null)
                tags.close();
        }
    }

    private void writeGpxFileTags(int id) throws SQLException, XMLStreamException {
        ResultSet tags = executeQuery("SELECT tag FROM gpx_file_tags WHERE gpx_id=" + id);

        while (tags.next()) {
            xmlw.writeStartElement("tag");
            xmlw.writeCharacters(sanitize(tags.getString(1)));
            xmlw.writeEndElement();
        }
    }

    private void writeTrackableTraces() throws SQLException, XMLStreamException, IOException, XMLException {
        ResultSet gpxFiles = null;
        PreparedStatement gpxPointsStatement = getPreparedStatement(
                "SELECT latitude, longitude, altitude, trackid, timestamp " +
                "FROM gps_points " +
                "WHERE gpx_id = ? " +
                "ORDER by trackid, timestamp");

        try {
            gpxFiles = executeQuery(
                    "SELECT id, timestamp, name, size, latitude, longitude, visibility "
                    + "FROM gpx_files "
                    + "WHERE inserted = true AND visible = true AND visibility = 'trackable'"
                    + "ORDER BY id");

            while (gpxFiles.next()) {
                xmlw.writeStartElement("gpxFile");
                xmlw.writeAttribute("id", gpxFiles.getString(1));
                xmlw.writeAttribute("timestamp", OSMUtils.dateFormat.format(gpxFiles.getTimestamp(2)));
                xmlw.writeAttribute("fileName", gpxFiles.getLong(1) + ".gpx.gz");
                xmlw.writeAttribute("points", gpxFiles.getString(4));
                xmlw.writeAttribute("startLatitude", OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(5))));
                xmlw.writeAttribute("startLongitude", OSMUtils.convertCoordinateToString(OSMUtils.convertCoordinateToInt(gpxFiles.getDouble(6))));
                xmlw.writeAttribute("visibility", gpxFiles.getString(7));

                xmlw.writeEndElement();

                // Query for the GPX points
                gpxPointsStatement.setLong(1, gpxFiles.getLong(1));
                ResultSet gpxPoints = gpxPointsStatement.executeQuery();

                File outputFile = new File(gpxOutputFolder, Long.toString(gpxFiles.getLong(1)) + ".gpx.gz");

                // Write individual trackable GPX file
                OutputStream out = new GZIPOutputStream(new FileOutputStream(outputFile));
                XMLStreamWriter2 writer = createXMLWriter(out);
                writeGpxFileStart(writer);
                writeTrackableGpxFile(writer, gpxPoints);
                writeGpxFileEnd(writer);
                writer.closeCompletely();

                // Remember the filename we wrote so it can be included in the
                // overally dump
                appendGpxFileToExportList(outputFile);
            }
        } finally {
            if (gpxFiles != null)
                gpxFiles.close();
        }
    }

    private void appendGpxFileToExportList(File gpxFile) throws IOException {
        fileListFile.write(gpxFile.getAbsolutePath());
        fileListFile.write(NEW_LINE);
    }

    private void writeTrackableGpxFile(XMLStreamWriter2 writer, ResultSet gpxPoints) throws XMLStreamException,
            SQLException {
        Integer trackId = null;

        while (gpxPoints.next()) {
            // Start a new <trk> if the track id has changed
            if (trackId == null) {
                trackId = gpxPoints.getInt(4);
                writeTrackElementStart(writer, trackId);
            } else if (gpxPoints.getInt(4) != trackId) {
                writeTrackElementEnd(writer);
                trackId = gpxPoints.getInt(4);
                writeTrackElementStart(writer, trackId);
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

            writer.writeEndElement(); // </trkpt>
        }

    }

    private void writePrivateTraces() throws SQLException, XMLStreamException, IOException, XMLException {
        ResultSet gpxFiles = null;
        PreparedStatement gpxPointsStatement = getPreparedStatement(
                "SELECT latitude, longitude, altitude "
                + "FROM gps_points WHERE gpx_id = ? "
                + "ORDER BY tile ASC, latitude ASC, longitude ASC");

        try {
            gpxFiles = executeQuery(
                    "SELECT id "
                    + "FROM gpx_files "
                    + "WHERE inserted = true AND visible = true AND visibility = 'private' "
                    + "ORDER BY id");

            // Only one file for all private traces
            xmlw.writeStartElement("gpxFile");
            xmlw.writeAttribute("fileName", "private.gpx.gz");
            xmlw.writeAttribute("visibility", "private");
            xmlw.writeEndElement();

            File outputFile = new File(gpxOutputFolder, "private.gpx.gz");
            OutputStream out = new GZIPOutputStream(new FileOutputStream(outputFile));
            XMLStreamWriter2 writer = createXMLWriter(out);
            appendGpxFileToExportList(outputFile);

            writeGpxFileStart(writer);
            writeTrackElementStart(writer, 1);
            while (gpxFiles.next()) {
                gpxPointsStatement.setLong(1, gpxFiles.getLong(1));
                ResultSet gpxPoints = gpxPointsStatement.executeQuery();
                writePrivateGpxFile(writer, gpxPoints);
            }
            writeGpxFileEnd(writer);

            writer.closeCompletely();
        } finally {
            if (gpxFiles != null)
                gpxFiles.close();
        }

    }

    private void writePrivateGpxFile(XMLStreamWriter2 writer, ResultSet gpxPoints) throws XMLStreamException,
            SQLException {
        while (gpxPoints.next()) {
            writer.writeStartElement("trkpt");

            writer.writeAttribute("lat", OSMUtils.convertCoordinateToString(gpxPoints.getInt(1)));
            writer.writeAttribute("lon", OSMUtils.convertCoordinateToString(gpxPoints.getInt(2)));

            gpxPoints.getDouble(3);
            if (!gpxPoints.wasNull()) {
                writer.writeStartElement("ele");
                writer.writeCharacters(OSMUtils.numberFormat.format(gpxPoints.getDouble(3)));
                writer.writeEndElement();
            }

            writer.writeEndElement(); // </trkpt>
        }

    }

    private void writeGpxFileStart(XMLStreamWriter2 writer) throws XMLStreamException {
        writer.writeStartDocument();
        writer.writeStartElement("gpx");
        writer.writeDefaultNamespace("http://www.topografix.com/GPX/1/0");
        writer.writeAttribute("version", "1.0");
        writer.writeAttribute("creator", "planet.gpx exporter");
    }

    private void writeGpxFileEnd(XMLStreamWriter2 writer) throws XMLStreamException {
        writeTrackElementEnd(writer);
        writer.writeEndElement(); // </gpx>
    }

    private void writeTrackElementStart(XMLStreamWriter writer, Integer trackId) throws XMLStreamException {
        writer.writeStartElement("trk");

        writer.writeStartElement("name");
        writer.writeCharacters("Track " + trackId.toString());
        writer.writeEndElement();

        writer.writeStartElement("number");
        writer.writeCharacters(trackId.toString());
        writer.writeEndElement();

        writer.writeStartElement("trkseg");
    }

    private void writeTrackElementEnd(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement(); // </trkseg>
        writer.writeEndElement(); // </trk>
    }

    private String sanitize(String s) {
        return SANITIZE_PATTERN.matcher(s).replaceAll("");
    }

}

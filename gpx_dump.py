import psycopg2
from xml.dom.minidom import Document
import argparse
import os
import sys
import datetime

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Dumps GPX files from the OSM railsport database schema.")

    # Postgres options
    parser.add_argument("--host",
        help="postgres server host",
        required=True)
    parser.add_argument("--user",
        help="postgres user name",
        required=True)
    parser.add_argument("--password",
        help="postgres user password",
        required=True)
    parser.add_argument("--database",
        help="postgres database name",
        required=True)

    # GPX dumping options
    parser.add_argument("--privacy",
        help="select which privacy levels to write out",
        choices=['public', 'identifiable', 'trackable', 'private'],
        default=['public', 'identifiable', 'trackable'])
    parser.add_argument("--output",
        help="output directory to fill with resulting GPX files",
        default=".")
    parser.add_argument("--metadata",
        help="file inside output directory to write metadata about uploaded GPX files (tags, visibility, etc.)",
        default="metadata.xml")

    args = parser.parse_args()

    if not os.path.exists(args.output):
        sys.stderr.write("Output directory doesn't exist.\n")
        sys.exit(-1)

    if os.path.exists("%s/%s" % (args.output, args.metadata)):
        sys.stderr.write("Metadata file already exists.\n")
        sys.exit(-1)

    doc = Document()
    metadata_file = open("%s/%s" % (args.output, args.metadata), 'w')
    filesElem = doc.createElement("gpxFiles")
    filesElem.setAttribute("generator", "OpenStreetMap gpx_dump.py")
    filesElem.setAttribute("timestamp", datetime.datetime.utcnow().isoformat())
    metadata_file.write(filesElem.toxml('utf-8'))

    conn = psycopg2.connect(database=args.database, user=args.user, password=args.password, host=args.host)
    file_cursor = conn.cursor(name='gpx_files')
    tags_cursor = conn.cursor(name='gpx_file_tags')
    point_cursor = conn.cursor(name='gpx_points')

    if 'public' in args.privacy:
        print "Writing public traces."
        file_cursor.execute("""SELECT id,user_id,timestamp,name,description,size,latitude,longitude,visibility
                               FROM gpx_files
                               WHERE inserted=true AND visible=true AND visibility='public'
                               ORDER BY id""")
        for row in file_cursor:
            # Write out the metadata about this GPX file to the metadata list
            filesElem = doc.createElement("gpxFile")
            filesElem.setAttribute("id", row[1])
            filesElem.setAttribute("timestamp", row[3].isoformat())
            filesElem.setAttribute("fileName", row[1])
            filesElem.setAttribute("description", row[5])
            filesElem.setAttribute("points", row[6])
            filesElem.setAttribute("startLatitude", row[7])
            filesElem.setAttribute("startLongitude", row[8])
            filesElem.setAttribute("visibility", row[9])

            tags_cursor.execute("""SELECT tag FROM gpx_file_tags WHERE gpx_id=%s""", (row[1]))

            for tag in tags_cursor:
                tagElem = doc.createElement("tag")
                tagElem.data = tag[1]
                filesElem.appendChild(tagElem)

            metadata_file.write(filesElem.toxml('utf-8'))

            # Write out GPX file
            # Important to note that we are not including timestamp here because it's public.
            # See http://wiki.openstreetmap.org/wiki/Visibility_of_GPS_traces
            point_cursor.execute("""SELECT latitude,longitude,altitude,trackid
                                    FROM gps_points
                                    WHERE gpx_id=%s
                                    ORDER BY trackid ASC, tile ASC, latitude ASC, longitude ASC""", (row[1]))

            gpxDoc = Document()
            gpxElem = gpxDoc.createElement("gpx")
            gpxElem.setAttribute("xmlns", "http://www.topografix.com/GPX/1/0")
            gpxElem.setAttribute("version", "1.0")
            gpxElem.setAttribute("creator", "OSM gpx_dump.py")

            trackid = None
            for point in point_cursor:
                if trackid is None or trackid != point[4]:
                    trackid = point[4]
                    trkElem = gpxDoc.createElement("trk")
                    nameElem = gpxDoc.createElement("name")
                    nameElem.data = "Track %s" % (trackid)
                    trkElem.appendChild(nameElem)

                    numberElem = gpxDoc.createElement("number")
                    numberElem.data = str(trackid)
                    trkElem.appendChild(numberElem)

                    segmentElem = gpxDoc.createElement("trkseg")
                    trkElem.appendChild(segmentElem)
                    gpxElem.appendChild(trkElem)

                ptElem = gpxDoc.createElement("trkpt")
                ptElem.setAttribute("lat", "%.7f" % float(point[1]) / 10 ^ 7)
                ptElem.setAttribute("lon", "%.7f" % float(point[2]) / 10 ^ 7)
                if point[3]:
                    eleElem = gpxDoc.createElement("ele")
                    eleElem.data = "ele", "%0.2f" % point[3]
                    ptElem.appendChild(eleElem)

                trkElem.appendChild(ptElem)

        print "Done writing public traces."

    if 'identifiable' in args.privacy:
        print "Writing identifiable traces."
        print "Done writing identifiable traces."

    if 'trackable' in args.privacy:
        print "Writing trackable traces."
        print "Done writing trackable traces."

    if 'private' in args.privacy:
        print "Skipping private traces."

    metadata_file.write('</gpxFiles>\n')
    metadata_file.close()

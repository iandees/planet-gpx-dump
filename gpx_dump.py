import psycopg2
from lxml import etree
import argparse
import os
import errno
import sys
import datetime

# Lat/lon in the gps_points schema is stored as an int with the decimal
# shifted by seven places.
MULTI_FACTOR = 10 ** 7


def status_line(text):
    sys.stdout.write(text)
    sys.stdout.write('\r')
    sys.stdout.flush()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Dumps GPX files from the OSM railsport database schema.")

    # Postgres options
    parser.add_argument("--host",
        help="postgres server host")
    parser.add_argument("--port",
        help="postgres server port",
        default="5432",
        type=int)
    parser.add_argument("--user",
        help="postgres user name")
    parser.add_argument("--password",
        help="postgres user password")
    parser.add_argument("--database",
        help="postgres database name",
        required=True)

    # GPX dumping options
    parser.add_argument("--privacy",
        help="select which privacy levels to write out",
        choices=['public', 'identifiable', 'trackable'],
        default=['public', 'identifiable', 'trackable'])
    parser.add_argument("--output",
        help="output directory to fill with resulting GPX files",
        default=".")

    # Extraneous options
    parser.add_argument("--enable-tags",
        help="enable dumping tags to the metadata for each gpx file",
        default=True,
        type=bool)
    parser.add_argument("--enable-uid-mapping",
        help="enable userid to username mapping",
        default=True,
        type=bool)

    args = parser.parse_args()

    if not os.path.exists(args.output):
        sys.stderr.write("Output directory doesn't exist.\n")
        sys.exit(-1)

    if os.path.exists("%s/metadata.xml" % (args.output)):
        sys.stderr.write("Metadata file already exists.\n")
        sys.exit(-1)

    metadata_file = open("%s/metadata.xml" % (args.output), 'w')
    metadata_file.write('<gpxFiles generator="OpenStreetMap gpx_dump.py" timestamp="%s">\n' % datetime.datetime.utcnow().replace(microsecond=0).isoformat())

    if args.host:
        conn = psycopg2.connect(database=args.database, port=args.port, user=args.user, password=args.password, host=args.host)
    else:
        conn = psycopg2.connect(database=args.database, port=args.port, user=args.user)
    file_cursor = conn.cursor(name='gpx_files')
    tags_cursor = conn.cursor()

    user_map = dict()
    if args.enable_uid_mapping:
        print "Mapping user IDs."
        user_cursor = conn.cursor(name='users')
        user_cursor.execute("""SELECT id,display_name FROM users""")
        for user in user_cursor:
            user_map[user[0]] = user[1]
        print "Mapped %s user ids." % (len(user_map))

    files_so_far = 0

    for d in args.privacy:
        path = '%s/%s' % (args.output, d)
        try:
            os.makedirs(path)
        except OSError as exc:
            if exc.errno == errno.EEXIST and os.path.isdir(path):
                pass
            else:
                raise

    print "Writing traces."
    file_cursor.execute("""SELECT id,user_id,timestamp,name,description,size,latitude,longitude,visibility
                           FROM gpx_files
                           WHERE inserted=true AND visible=true AND visibility IN ('public', 'trackable', 'identifiable')
                           ORDER BY id""")
    for row in file_cursor:
        if row[8] == 'private':
            continue

        # Write out the metadata about this GPX file to the metadata list
        filesElem = etree.Element("gpxFile")
        filesElem.attrib["id"] = str(row[0])
        filesElem.attrib["timestamp"] = row[2].isoformat()
        filesElem.attrib["description"] = row[4]
        filesElem.attrib["points"] = str(row[5])
        filesElem.attrib["startLatitude"] = "%0.7f" % (row[6],)
        filesElem.attrib["startLongitude"] = "%0.7f" % (row[7],)
        filesElem.attrib["visibility"] = row[8]

        # Only write out user information if it's identifiable or public
        if row[1] and row[8] in ('identifiable', 'public'):
            filesElem.attrib["uid"] = str(row[1])

            if row[1] in user_map:
                filesElem.attrib["user"] = user_map.get(row[1])

        if args.enable_tags:
            tags_cursor.execute("""SELECT tag FROM gpx_file_tags WHERE gpx_id=%s""", [row[0]])

            for tag in tags_cursor:
                tagElem = etree.SubElement(filesElem, "tag")
                tagElem.text = tag[0]

        # Write out GPX file
        point_cursor = conn.cursor(name='gpx_points')
        point_cursor.execute("""SELECT latitude,longitude,altitude,trackid,"timestamp"
                                FROM gps_points
                                WHERE gpx_id=%s
                                ORDER BY trackid ASC, "timestamp" ASC""", [row[0]])

        gpxElem = etree.Element("gpx")
        gpxElem.attrib["xmlns"] = "http://www.topografix.com/GPX/1/0"
        gpxElem.attrib["version"] = "1.0"
        gpxElem.attrib["creator"] = "OSM gpx_dump.py"

        trackid = None
        for point in point_cursor:
            if trackid is None or trackid != point[3]:
                trackid = point[3]
                trkElem = etree.SubElement(gpxElem, "trk")
                nameElem = etree.SubElement(trkElem, "name")
                nameElem.text = "Track %s" % (trackid)

                numberElem = etree.SubElement(trkElem, "number")
                numberElem.text = str(trackid)

                segmentElem = etree.SubElement(trkElem, "trkseg")

            ptElem = etree.SubElement(segmentElem, "trkpt")
            ptElem.attrib["lat"] = "%0.7f" % (float(point[0]) / MULTI_FACTOR)
            ptElem.attrib["lon"] = "%0.7f" % (float(point[1]) / MULTI_FACTOR)
            if point[2]:
                eleElem = etree.SubElement(ptElem, "ele")
                eleElem.text = "%0.2f" % point[2]

            if point[4] and row[8] in ('identifiable', 'trackable'):
                timeElem = etree.SubElement(ptElem, "time")
                timeElem.text = point[4].isoformat()

        point_cursor.close()

        file_path = "%s/%s/%07d.gpx" % (args.output, row[8], row[0])
        etree.ElementTree(gpxElem).write(file_path, xml_declaration=True, pretty_print=True, encoding='utf-8')

        filesElem.attrib["filename"] = file_path
        metadata_file.write(etree.tostring(filesElem, pretty_print=True, encoding='utf-8'))

        files_so_far += 1

        status_line("Wrote out %7d GPX files." % files_so_far)

    print "Wrote out %7d GPX files." % files_so_far

    metadata_file.write('</gpxFiles>\n')
    metadata_file.close()

import psycopg2
import psycopg2.extras
from lxml import etree
import argparse
import os
import errno
import sys
import datetime

# Lat/lon in the gps_points schema is stored as an int with the decimal
# shifted by seven places.
MULTI_FACTOR = 10 ** 7


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
    parser.add_argument("--disable-tags",
        help="disable dumping tags to the metadata for each gpx file",
        dest="enable_tags",
        default=True,
        action="store_false")
    parser.add_argument("--disable-uid-mapping",
        help="disable userid to username mapping",
        dest="enable_uid_mapping",
        default=True,
        action="store_false")

    args = parser.parse_args()

    if not os.path.exists(args.output):
        sys.stderr.write("Output directory doesn't exist.\n")
        sys.exit(-1)

    if os.path.exists("%s/metadata.xml" % (args.output)):
        sys.stderr.write("Metadata file already exists.\n")
        sys.exit(-1)

    metadata_file = open("%s/metadata.xml" % (args.output), 'w')
    metadata_file.write('<gpxFiles version="1.0" generator="OpenStreetMap gpx_dump.py" timestamp="%sZ">\n' % datetime.datetime.utcnow().replace(microsecond=0).isoformat())

    if args.host:
        conn = psycopg2.connect(database=args.database, port=args.port, user=args.user, password=args.password, host=args.host)
    else:
        conn = psycopg2.connect(database=args.database, port=args.port, user=args.user)
    file_cursor = conn.cursor(name='gpx_files', cursor_factory=psycopg2.extras.DictCursor)
    tags_cursor = conn.cursor(cursor_factory=psycopg2.extras.DictCursor)

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
    file_cursor.execute("""SELECT id,user_id,"timestamp",name,description,size,latitude,longitude,visibility
                           FROM gpx_files
                           WHERE inserted=true AND visible=true AND visibility IN ('public', 'trackable', 'identifiable')
                           ORDER BY id""")
    for row in file_cursor:
        if row['visibility'] == 'private':
            continue

        # Write out the metadata about this GPX file to the metadata list
        filesElem = etree.Element("gpxFile")
        filesElem.attrib["id"] = str(row['id'])
        filesElem.attrib["timestamp"] = row['timestamp'].isoformat() + "Z"
        filesElem.attrib["points"] = str(row['size'])
        filesElem.attrib["lat"] = "%0.7f" % (row['latitude'],)
        filesElem.attrib["lon"] = "%0.7f" % (row['longitude'],)
        filesElem.attrib["visibility"] = row['visibility']

        descriptionElem = etree.SubElement(filesElem, 'description')
        descriptionElem.text = row['description']

        # Only write out user information if it's identifiable or public
        if row['user_id'] and row['visibility'] in ('identifiable', 'public'):
            filesElem.attrib["uid"] = str(row['user_id'])

            if row['user_id'] in user_map:
                filesElem.attrib["user"] = user_map.get(row['user_id'])

        if args.enable_tags:
            tagsElem = etree.SubElement(filesElem, "tags")

            tags_cursor.execute("""SELECT tag FROM gpx_file_tags WHERE gpx_id=%s""", [row['id']])

            for tag in tags_cursor:
                tagElem = etree.SubElement(tagsElem, "tag")
                tagElem.text = tag[0]

        # Write out GPX file
        point_cursor = conn.cursor(name='gpx_points', cursor_factory=psycopg2.extras.DictCursor)
        point_cursor.execute("""SELECT latitude,longitude,altitude,trackid,"timestamp"
                                FROM gps_points
                                WHERE gpx_id=%s
                                ORDER BY trackid ASC, "timestamp" ASC""", [row['id']])

        gpxElem = etree.Element("gpx")
        gpxElem.attrib["xmlns"] = "http://www.topografix.com/GPX/1/0"
        gpxElem.attrib["version"] = "1.0"
        gpxElem.attrib["creator"] = "OSM gpx_dump.py"

        trackid = None
        for point in point_cursor:
            if trackid is None or trackid != point['trackid']:
                trackid = point['trackid']
                trkElem = etree.SubElement(gpxElem, "trk")
                nameElem = etree.SubElement(trkElem, "name")
                nameElem.text = "Track %s" % (trackid)

                numberElem = etree.SubElement(trkElem, "number")
                numberElem.text = str(trackid)

                segmentElem = etree.SubElement(trkElem, "trkseg")

            ptElem = etree.SubElement(segmentElem, "trkpt")
            ptElem.attrib["lat"] = "%0.7f" % (float(point['latitude']) / MULTI_FACTOR)
            ptElem.attrib["lon"] = "%0.7f" % (float(point['longitude']) / MULTI_FACTOR)
            if point['altitude']:
                eleElem = etree.SubElement(ptElem, "ele")
                eleElem.text = "%0.2f" % point['altitude']

            if point['timestamp'] and row['visibility'] in ('identifiable', 'trackable'):
                timeElem = etree.SubElement(ptElem, "time")
                timeElem.text = point['timestamp'].isoformat() + "Z"

        point_cursor.close()

        file_path = "%s/%s/%09d.gpx" % (args.output, row['visibility'], row['id'])
        etree.ElementTree(gpxElem).write(file_path, xml_declaration=True, pretty_print=True, encoding='utf-8')

        filesElem.attrib["filename"] = "%s/%09d.gpx" % (row['visibility'], row['id'])
        metadata_file.write(etree.tostring(filesElem, pretty_print=True, encoding='utf-8'))

        files_so_far += 1

        if (files_so_far % 100 == 0):
            print "Wrote out %9d GPX files." % files_so_far

    metadata_file.write('</gpxFiles>\n')
    metadata_file.close()

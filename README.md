planet-gpx-dump
===============

Tools to dump all GPS traces collected by/for the OpenStreetMap project.

gpx_dump.py
-----------

A python tool designed to query the OpenStreetMap API database (the one behind the [railsport](http://wiki.openstreetmap.org/wiki/The_Rails_Port)) and write out a collection of GPX files based on the user-uploaded traces. In it's current form, this script will only write out `public`, `identifiable`, and `trackable` traces. Due to privacy concerns and the technical implications around anonymizing data marked as private, the tool will not export traces marked as `private`.

This repo also contains `gpx_tables.sql`, a representative dump of the actual GPX-related table schema running on the OSM database server as of the end of March 2013. It also inserts a few dummy traces to test with at the end.

Running
-------

The python application is fairly self-documenting. Run `python gpx_dump.py --help` to get a help message like this:

```
usage: gpx_dump.py [-h] [--host HOST] [--port PORT] [--user USER]
                   [--password PASSWORD] --database DATABASE
                   [--privacy {public,identifiable,trackable}]
                   [--output OUTPUT] [--disable-tags] [--disable-uid-mapping]

Dumps GPX files from the OSM railsport database schema.

optional arguments:
  -h, --help            show this help message and exit
  --host HOST           postgres server host
  --port PORT           postgres server port
  --user USER           postgres user name
  --password PASSWORD   postgres user password
  --database DATABASE   postgres database name
  --privacy {public,identifiable,trackable}
                        select which privacy levels to write out
  --output OUTPUT       output directory to fill with resulting GPX files
  --disable-tags        disable dumping tags to the metadata for each gpx file
  --disable-uid-mapping
                        disable userid to username mapping
```

There are database connection options and output options. Use `--host`, `--port`, `--user`, `--password`, and `--database` to specify how to connect to the PostgreSQL database. The output options specify which privacy levels to output and whether or not to disable tags and username mapping.

Output Format
-------------

The gpx_dump.py tool creates a directory for each of the privacy levels and a file named `metadata.xml` in the directory specified by the `--output` option. The GPX files created from the export process will end up in these directories according to their privacy setting. The individual files will be named using their database ID padded by several zeroes. The GPX files will be [GPX 1.0](http://www.topografix.com/gpx_manual.asp) compliant XML documents.

The metadata XML file will contain one `<gpxFile>` element for each GPX file created. It has several attributes:

Name       | Description
-----------|------------
id         | The database ID for the gpx file.
timestamp  | The timestamp for when the trace was created in the database.
points     | The number of points in the GPX file.
lat        | The latitude for the GPX file. By default, this is the latitude for the first point in the file but can be changed by the user.
lon        | The longitude for the GPX file. By default, this is the longtiude for the first point in the file but can be changed by the user.
visibility | The visibility of the GPX file as specified by the user. One of `private`, `identifiable`, `public`, `trackable`.
uid        | The OSM user ID for the user that uploaded the trace. Note that this will only appear for `identifiable` and `public` traces.
user       | The OSM display name for the user that uploaded the trace. Note that this will only appear for `identifiable` and `public` traces and if "user id mapping" was enabled at run time.
filename   | The file name of the GPX trace (relative to the metadata.xml file).

Within each `<gpxFile>` element can exist:
- at most one `<description>` tag containing the description of the GPX trace as specified by the user.
- at most one `<tags>` element containing zero or more `<tag>` elements containing the tags for the GPX trace as specified by the user.

All times (in the GPX and metadata.xml files) are in UTC.

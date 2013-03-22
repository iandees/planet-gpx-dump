import psycopg2
import argparse

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
        required=True,
        help="output directory to fill with resulting GPX files")

    args = parser.parse_args()

    conn = psycopg2.connect(database=args.database, user=args.user, password=args.password, host=args.host)

    print args

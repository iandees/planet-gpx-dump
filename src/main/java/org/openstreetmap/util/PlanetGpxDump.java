package org.openstreetmap.util;

import org.apache.commons.cli.*;
import org.openstreetmap.util.exceptions.DatabaseException;
import org.openstreetmap.util.exceptions.XMLException;

import java.io.File;
import java.io.IOException;

/**
 * A program to dump all OpenStreetMap GPS-Traces
 */
public class PlanetGpxDump {

    public static void main(String[] args) {
        CommandLine cmd = parseCommandLine(args);

        String connectionUrl = System.getenv("CONNECTION_PARAMS");
        if (connectionUrl == null && !cmd.hasOption("connection")) {
            System.err.println("Please specify the parameters needed for the database connection. Start the program with the parameter --help to get information on how to specify the parameter.");
            System.exit(1);
        }
        if (cmd.hasOption("connection")) {
            connectionUrl = cmd.getOptionValue("connection");
        }
        connectionUrl = prepareConnectionUrl(connectionUrl);

        File metadataFile = new File(cmd.getOptionValue("m"));
        File fileListFile = new File(cmd.getOptionValue("f"));

        if (metadataFile.exists() || fileListFile.exists()) {
          System.err.println("The metadata file or file list file already exist. Please delete or move them.");
          System.exit(1);
        }

        File gpxOutputFolder = new File(cmd.getOptionValue("e"));
        if (!gpxOutputFolder.exists()) {
          System.err.println("The output folder for the GPX files doesn't exist. Please create it first.");
          System.exit(1);
        }

        try {
            Dumper dumper = new Dumper(connectionUrl, metadataFile, fileListFile, gpxOutputFolder);
            dumper.dump();
            System.exit(0);
        } catch (XMLException e) {
            System.err.println("Something went wrong with the XML output.");
            System.err.println(e.getMessage());
            e.printStackTrace();
            if (e.getCause() != null) {
                System.err.print(e.getCause().getMessage());
            }
            System.exit(1);
        } catch (DatabaseException e) {
            System.err.println("Something went wrong with the Database or one of the queries.");
            System.err.println(e.getMessage());
            e.printStackTrace();
            if (e.getCause() != null) {
                System.err.print(e.getCause().getMessage());
            }
            System.exit(1);
        } catch (IOException e) {
          e.printStackTrace();
        }

    }

    private static CommandLine parseCommandLine(String[] args) {
        Options options = new Options();
        options.addOption("connection", true, "specifies the connection parameters. This overrides a CONNECTION_PARAMS environment variable");
        options.addOption("m", "metadatafilename", true, "[required] specifies the name of the XML file which includes the metadata for all the GPX files. Must not exist.");
        options.addOption("f", "filelistname", true, "[required] specifies the name of the file where the list of GPX files to export is written to. Must not exist.");
        options.addOption("e", "extragpxfolder", true, "[required] specifies the folder where the Private and Trackable traces should be written to. Must exist.");

        options.addOption("h", "help", false, "print this help and exit");

        CommandLineParser parser = new GnuParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            printHelp(options);
            System.exit(1);
        }

        if (cmd.hasOption("help") || !cmd.hasOption("m") || !cmd.hasOption("f") || !cmd.hasOption("e")) {
            printHelp(options);
            System.exit(0);
        }

        return cmd;
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("planetgpxdump", "The following options are allowed:", options,
                "The connection parameters have to be provided in the environment variable CONNECTION_PARAMS in the format: \n" +
                "[//host[:port]/]database?user=<username>&password=<password>\n" +
                "* [host] defaults to 'localhost'. To specify an IPv6 address your must enclose the host parameter with square brackets.\n" +
                "* [port] defaults to '5432'.\n" +
                "* username and password may be optional depending on the configuration of PostgreSQL", true);
    }

    private static String prepareConnectionUrl(String connectionUrl) {
        if (connectionUrl.startsWith("jdbc:postgresql:")) {
            return connectionUrl;
        }

        return "jdbc:postgresql:" + connectionUrl;
    }
}

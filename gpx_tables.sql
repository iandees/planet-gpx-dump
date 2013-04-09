--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

CREATE TYPE user_status_enum AS ENUM (
    'pending',
    'active',
    'confirmed',
    'suspended',
    'deleted'
);
CREATE TYPE gpx_visibility_enum AS ENUM (
    'private',
    'public',
    'trackable',
    'identifiable'
);
CREATE TABLE users (
    id bigint NOT NULL,
    display_name character varying(255) DEFAULT ''::character varying NOT NULL,
    status user_status_enum DEFAULT 'pending'::user_status_enum NOT NULL
);
ALTER TABLE public.users OWNER TO openstreetmap;
ALTER TABLE ONLY users 
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: gps_points; Type: TABLE; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE TABLE gps_points (
    altitude double precision,
    trackid integer NOT NULL,
    latitude integer NOT NULL,
    longitude integer NOT NULL,
    gpx_id bigint NOT NULL,
    "timestamp" timestamp without time zone,
    tile bigint
);


--
-- Name: gpx_file_tags; Type: TABLE; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE TABLE gpx_file_tags (
    gpx_id bigint DEFAULT 0 NOT NULL,
    tag character varying(255) NOT NULL,
    id bigint NOT NULL
);



ALTER TABLE public.gpx_file_tags OWNER TO openstreetmap;

--
-- Name: gpx_file_tags_id_seq; Type: SEQUENCE; Schema: public; Owner: openstreetmap
--

CREATE SEQUENCE gpx_file_tags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.gpx_file_tags_id_seq OWNER TO openstreetmap;

--
-- Name: gpx_file_tags_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: openstreetmap
--

ALTER SEQUENCE gpx_file_tags_id_seq OWNED BY gpx_file_tags.id;


--
-- Name: gpx_files; Type: TABLE; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE TABLE gpx_files (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    visible boolean DEFAULT true NOT NULL,
    name character varying(255) DEFAULT ''::character varying NOT NULL,
    size bigint,
    latitude double precision,
    longitude double precision,
    "timestamp" timestamp without time zone NOT NULL,
    description character varying(255) DEFAULT ''::character varying NOT NULL,
    inserted boolean NOT NULL,
    visibility gpx_visibility_enum DEFAULT 'public'::gpx_visibility_enum NOT NULL
);

ALTER TABLE public.gpx_files OWNER TO openstreetmap;

--
-- Name: gpx_files_id_seq; Type: SEQUENCE; Schema: public; Owner: openstreetmap
--

CREATE SEQUENCE gpx_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.gpx_files_id_seq OWNER TO openstreetmap;

--
-- Name: gpx_files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: openstreetmap
--

ALTER SEQUENCE gpx_files_id_seq OWNED BY gpx_files.id;


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: openstreetmap
--

ALTER TABLE ONLY gpx_file_tags ALTER COLUMN id SET DEFAULT nextval('gpx_file_tags_id_seq'::regclass);


--
-- Name: id; Type: DEFAULT; Schema: public; Owner: openstreetmap
--

ALTER TABLE ONLY gpx_files ALTER COLUMN id SET DEFAULT nextval('gpx_files_id_seq'::regclass);


--
-- Name: gpx_file_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: openstreetmap; Tablespace: 
--

ALTER TABLE ONLY gpx_file_tags
    ADD CONSTRAINT gpx_file_tags_pkey PRIMARY KEY (id);


--
-- Name: gpx_files_pkey; Type: CONSTRAINT; Schema: public; Owner: openstreetmap; Tablespace: 
--

ALTER TABLE ONLY gpx_files
    ADD CONSTRAINT gpx_files_pkey PRIMARY KEY (id);


--
-- Name: gpx_file_tags_gpxid_idx; Type: INDEX; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE INDEX gpx_file_tags_gpxid_idx ON gpx_file_tags USING btree (gpx_id);


--
-- Name: gpx_file_tags_tag_idx; Type: INDEX; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE INDEX gpx_file_tags_tag_idx ON gpx_file_tags USING btree (tag);


--
-- Name: gpx_files_timestamp_idx; Type: INDEX; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE INDEX gpx_files_timestamp_idx ON gpx_files USING btree ("timestamp");


--
-- Name: gpx_files_user_id_idx; Type: INDEX; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE INDEX gpx_files_user_id_idx ON gpx_files USING btree (user_id);


--
-- Name: gpx_files_visible_visibility_idx; Type: INDEX; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE INDEX gpx_files_visible_visibility_idx ON gpx_files USING btree (visible, visibility);


--
-- Name: points_gpxid_idx; Type: INDEX; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE INDEX points_gpxid_idx ON gps_points USING btree (gpx_id);


--
-- Name: points_tile_idx; Type: INDEX; Schema: public; Owner: openstreetmap; Tablespace: 
--

CREATE INDEX points_tile_idx ON gps_points USING btree (tile);


--
-- Name: gps_points_gpx_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: openstreetmap
--

ALTER TABLE ONLY gps_points
    ADD CONSTRAINT gps_points_gpx_id_fkey FOREIGN KEY (gpx_id) REFERENCES gpx_files(id);


--
-- Name: gpx_file_tags_gpx_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: openstreetmap
--

ALTER TABLE ONLY gpx_file_tags
    ADD CONSTRAINT gpx_file_tags_gpx_id_fkey FOREIGN KEY (gpx_id) REFERENCES gpx_files(id);


--
-- Name: gpx_files_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: openstreetmap
--

ALTER TABLE ONLY gpx_files
    ADD CONSTRAINT gpx_files_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);


--
-- Name: gps_points; Type: ACL; Schema: public; Owner: openstreetmap
--

REVOKE ALL ON TABLE gps_points FROM PUBLIC;
REVOKE ALL ON TABLE gps_points FROM openstreetmap;
GRANT ALL ON TABLE gps_points TO openstreetmap;

--
-- Name: gpx_file_tags; Type: ACL; Schema: public; Owner: openstreetmap
--

REVOKE ALL ON TABLE gpx_file_tags FROM PUBLIC;
REVOKE ALL ON TABLE gpx_file_tags FROM openstreetmap;
GRANT ALL ON TABLE gpx_file_tags TO openstreetmap;

--
-- Name: gpx_file_tags_id_seq; Type: ACL; Schema: public; Owner: openstreetmap
--

REVOKE ALL ON SEQUENCE gpx_file_tags_id_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE gpx_file_tags_id_seq FROM openstreetmap;
GRANT ALL ON SEQUENCE gpx_file_tags_id_seq TO openstreetmap;

--
-- Name: gpx_files; Type: ACL; Schema: public; Owner: openstreetmap
--

REVOKE ALL ON TABLE gpx_files FROM PUBLIC;
REVOKE ALL ON TABLE gpx_files FROM openstreetmap;
GRANT ALL ON TABLE gpx_files TO openstreetmap;


--
-- Name: gpx_files_id_seq; Type: ACL; Schema: public; Owner: openstreetmap
--

REVOKE ALL ON SEQUENCE gpx_files_id_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE gpx_files_id_seq FROM openstreetmap;
GRANT ALL ON SEQUENCE gpx_files_id_seq TO openstreetmap;


--
-- PostgreSQL database dump complete
--


--- Insert some test data.

INSERT INTO users (id, display_name, status) VALUES
(100, 'abcdefg', 'pending'),
(200, 'hijklmn', 'active'),
(300, 'opqrstu', 'confirmed'),
(400, 'vwxyz22', 'deleted'),
(500, 'überman', 'active');

INSERT INTO gpx_files (id,user_id,visible,name,size,latitude,longitude,"timestamp",description,inserted,visibility) VALUES
(10,100,true,'trace a',1500,45.0,-90.0,'2013-01-01T22:00:00Z','trace a desc',true,'public'),
(11,200,true,'trace b',1500,45.1,-90.1,'2013-01-02T23:30:00Z','trace b desc',true,'identifiable'),
(12,300,true,'trace c',1500,45.1,-90.1,'2013-01-02T21:30:00Z','trace c desc',true,'trackable'),
(13,400,true,'trace d',1500,45.1,-90.1,'2013-01-02T20:30:00Z','trace d desc',true,'private'),
(14,500,true,'trace å∫ç∂éƒ©ü!',1500,45.1,-90.1,'2013-01-02T20:30:00Z','trace å∫ç∂éƒ©ü!<>/',true,'trackable')
;

INSERT INTO gps_points (gpx_id,trackid,"timestamp",tile,latitude,longitude,altitude) VALUES
(10,1,'2013-01-01T22:00:00Z',0,451234567,-911234567,45.2),
(10,1,'2013-01-01T22:00:01Z',0,452234567,-912234567,45.6),
(10,2,'2013-01-01T22:00:00Z',0,461234567,-901234567,46.2),
(10,2,'2013-01-01T22:00:01Z',0,462234567,-902234567,46.5);
INSERT INTO gps_points (gpx_id,trackid,"timestamp",tile,latitude,longitude,altitude) VALUES
(11,1,'2013-01-01T22:00:00Z',0,451234567,-911234567,45.2),
(11,1,'2013-01-01T22:00:01Z',0,452234567,-912234567,45.6),
(11,2,'2013-01-01T22:00:00Z',0,461234567,-901234567,46.2),
(11,2,'2013-01-01T22:00:01Z',0,462234567,-902234567,46.5);
INSERT INTO gps_points (gpx_id,trackid,"timestamp",tile,latitude,longitude,altitude) VALUES
(12,1,'2013-01-01T22:00:00Z',0,451234567,-911234567,45.2),
(12,1,'2013-01-01T22:00:01Z',0,452234567,-912234567,45.6),
(12,2,'2013-01-01T22:00:00Z',0,461234567,-901234567,46.2),
(12,2,'2013-01-01T22:00:01Z',0,462234567,-902234567,46.5);
INSERT INTO gps_points (gpx_id,trackid,"timestamp",tile,latitude,longitude,altitude) VALUES
(13,1,'2013-01-01T22:00:00Z',0,451234567,-911234567,45.2),
(13,1,'2013-01-01T22:00:01Z',0,452234567,-912234567,45.6),
(13,2,'2013-01-01T22:00:00Z',0,461234567,-901234567,46.2),
(13,2,'2013-01-01T22:00:01Z',0,462234567,-902234567,46.5);
INSERT INTO gps_points (gpx_id,trackid,"timestamp",tile,latitude,longitude,altitude) VALUES
(14,1,'2013-01-01T22:00:00Z',0,451234567,-911234567,45.2),
(14,1,'2013-01-01T22:00:01Z',0,452234567,-912234567,45.6),
(14,2,'2013-01-01T22:00:00Z',0,461234567,-901234567,46.2),
(14,2,'2013-01-01T22:00:01Z',0,462234567,-902234567,46.5),
(14,3,'0036-11-04 15:12:36',0,462234567,-902234567,46.5),
(14,3,'40036-11-04 15:12:36',0,462234567,-902234567,46.5),
(14,3,'0001-11-04 15:51:23 BC',0,462234567,-902234567,46.5);

INSERT INTO gpx_file_tags (gpx_id,tag) VALUES
(10,'tag a'),
(11,'tag b'),
(14,'tag ü'||chr(19)||chr(19));

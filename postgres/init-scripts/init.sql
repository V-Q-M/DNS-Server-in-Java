
CREATE DOMAIN uint AS bigint CHECK(VALUE >= 0 AND VALUE < 2^32);

CREATE DOMAIN usmallint AS int CHECK(VALUE >= 0 AND VALUE < 2^16);

CREATE TYPE RRTypeEnum AS ENUM ('A','AAAA','SOA','TXT','MX', 'PTR', 'NS','CNAME');

CREATE TYPE RRClassEnum AS ENUM ('IN');

-- CacheTables
CREATE TABLE ResourceRecord (
    name text,
    rrtype RRTypeEnum,
    rrclass RRClassEnum,
    TTL uint,
    rdlength usmallint,
    inserted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ
);

CREATE TABLE ns_rdata (
    nsdname text
) INHERITS (ResourceRecord);

CREATE TABLE cname_rdata (
    cname text
) INHERITS (ResourceRecord);

CREATE TABLE a_rdata (
    ipv4 bytea CHECK (octet_length(ipv4)=4)
) INHERITS (ResourceRecord);

CREATE TABLE aaaa_rdata (
    ipv6 bytea CHECK (octet_length(ipv6)=16)
) INHERITS (ResourceRecord);

CREATE TABLE ptr_rdata (
    ptrdname text
) INHERITS (ResourceRecord);

CREATE TABLE txt_rdata (
    txtdata text[]
) INHERITS (ResourceRecord);

CREATE TABLE mx_rdata (
    pref usmallint,
    exchange text
) INHERITS (ResourceRecord);

CREATE TABLE soa_rdata (
    mname text,
    rname text,
    serial uint,
    refresh uint,
    retry uint,
    expire uint,
    minimum uint
) INHERITS (ResourceRecord);

-- ZoneTables
CREATE TABLE ZoneResourceRecord (
    zone text,
    name text,
    rrtype RRTypeEnum,
    rrclass RRClassEnum,
    TTL uint,
    rdlength usmallint
);

CREATE TABLE zone_ns_rdata (
    nsdname text
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_cname_rdata (
    cname text
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_a_rdata (
    ipv4 bytea CHECK (octet_length(ipv4)=4)
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_aaaa_rdata (
    ipv6 bytea CHECK (octet_length(ipv6)=16)
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_ptr_rdata (
    ptrdname text
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_txt_rdata (
   txtdata text[]
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_mx_rdata (
    pref usmallint,
    exchange text
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_soa_rdata (
    mname text,
    rname text,
    serial uint,
    refresh uint,
    retry uint,
    expire uint,
    minimum uint
) INHERITS (ZoneResourceRecord);
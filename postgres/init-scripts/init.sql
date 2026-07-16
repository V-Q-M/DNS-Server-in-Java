
CREATE DOMAIN uint AS bigint CHECK(VALUE >= 0 AND VALUE < 2^32);

CREATE DOMAIN usmallint AS int CHECK(VALUE >= 0 AND VALUE < 2^16);

CREATE TYPE RRTypeEnum AS ENUM (
    'A',
    'AAAA',
    'SOA',
    'TXT',
    'MX',
    'PTR',
    'NS',
    'CNAME',
    'DNSKEY',
    'DS',
    'RRSIG',
    'NSEC',
    'NSEC3',
    'NSEC3PARAM'
);

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

CREATE TABLE dnskey_rdata (
    flags usmallint,
    protocol smallint CHECK(protocol >= 0 AND protocol <= 255),
    algorithm smallint CHECK(algorithm >= 0 AND algorithm <= 255),
    publickey bytea
) INHERITS (ResourceRecord);

CREATE TABLE ds_rdata (
    key_tag usmallint,
    algorithm smallint CHECK(algorithm >= 0 AND algorithm <= 255),
    digest_type smallint CHECK(digest_type >= 0 AND digest_type <= 255),
    digest bytea
) INHERITS (ResourceRecord);

CREATE TABLE rrsig_rdata (
                             type_covered uint,
                             algorithm smallint CHECK(algorithm >= 0 AND algorithm <= 255),
                             labels smallint CHECK(labels >= 0 AND labels <= 255),
                             original_ttl uint,
                             expiration uint,
                             inception uint,
                             key_tag usmallint,
                             signer_name text,
                             signature bytea
) INHERITS (ResourceRecord);

CREATE TABLE nsec_rdata (
                            next_domain_name text,
                            type_bit_maps bytea
) INHERITS (ResourceRecord);

CREATE TABLE nsec3_rdata (
                             hash_algorithm smallint CHECK(hash_algorithm >= 0 AND hash_algorithm <= 255),
                             flags smallint CHECK(flags >= 0 AND flags <= 255),
                             iterations usmallint,
                             salt bytea,
                             next_hashed_owner_name bytea,
                             type_bit_maps bytea
) INHERITS (ResourceRecord);

CREATE TABLE nsec3param_rdata (
                                  hash_algorithm smallint CHECK(hash_algorithm >= 0 AND hash_algorithm <= 255),
                                  flags smallint CHECK(flags >= 0 AND flags <= 255),
                                  iterations usmallint,
                                  salt bytea
) INHERITS (ResourceRecord);

CREATE TABLE unknown_rdata (
                               type int CHECK(type >= 0 AND type < 65536),
                               data bytea
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


CREATE TABLE zone_dnskey_rdata (
                                   flags usmallint,
                                   protocol smallint CHECK(protocol >= 0 AND protocol <= 255),
                                   algorithm smallint CHECK(algorithm >= 0 AND algorithm <= 255),
                                   publickey bytea
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_ds_rdata (
                               key_tag usmallint,
                               algorithm smallint CHECK(algorithm >= 0 AND algorithm <= 255),
                               digest_type smallint CHECK(digest_type >= 0 AND digest_type <= 255),
                               digest bytea
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_rrsig_rdata (
                                  type_covered uint,
                                  algorithm smallint CHECK(algorithm >= 0 AND algorithm <= 255),
                                  labels smallint CHECK(labels >= 0 AND labels <= 255),
                                  original_ttl uint,
                                  expiration uint,
                                  inception uint,
                                  key_tag usmallint,
                                  signer_name text,
                                  signature bytea
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_nsec_rdata (
                                 next_domain_name text,
                                 type_bit_maps bytea
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_nsec3_rdata (
                                  hash_algorithm smallint CHECK(hash_algorithm >= 0 AND hash_algorithm <= 255),
                                  flags smallint CHECK(flags >= 0 AND flags <= 255),
                                  iterations usmallint,
                                  salt bytea,
                                  next_hashed_owner_name bytea,
                                  type_bit_maps bytea
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_nsec3param_rdata (
                                       hash_algorithm smallint CHECK(hash_algorithm >= 0 AND hash_algorithm <= 255),
                                       flags smallint CHECK(flags >= 0 AND flags <= 255),
                                       iterations usmallint,
                                       salt bytea
) INHERITS (ZoneResourceRecord);

CREATE TABLE zone_unknown_rdata (
                                    type int CHECK(type >= 0 AND type < 65536),
                                    data bytea
) INHERITS (ZoneResourceRecord);

-- DashboardTables
CREATE TYPE ResponseSourceEnum AS ENUM (
    'ZONE',
    'CACHE',
    'FORWARDER',
    'RECURSIVE_RESOLVER',
    'STUB'
);
CREATE TYPE Protocol AS ENUM (
    'TCP',
    'UDP'
);

CREATE TABLE dashboard (
    transaction_id TEXT,
    received_at TIMESTAMP,
    client_ip inet,
    client_port INTEGER,
    protocol Protocol,
    qname TEXT,
    qtype INT,
    rcode INT,
    response_source ResponseSourceEnum,
    responded_at TIMESTAMP,
    resolution_trace JSON
);

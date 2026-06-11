# Teamproject **Implementing a DNS server**

## Group members
### Group 1
- Hölz, Robin, 6979700, robin.hoelz@student.uni-tuebingen.de
- Müller, Viktor, 6349273, viktor.mueller@student.uni-tuebingen.de
- Philippin, Johannes, 6994772, johannes.philippin@student.uni-tuebingen.de
- Ruoff, Laurenz, 6978381, laurenz.ruoff@student.uni-tuebingen.de
- Bobes, Anas, 6688712, Anas.Bobes@student.uni-tuebingen.de
 
### Group 2
- Alnajar, Yaman, 5779680, yaman.alnajar@student.uni-tuebingen.de
- Baum, Kevin, 6386894, kevin.baum@student.uni-tuebingen.de
- Patnaik, Noemi, 6019008, noemi.patnaik@student.uni-tuebingen.de
- Zabel, Natalie, 6677093, natalie-aislyn.zabel@student.uni-tuebingen.de
 

## About
- Receives and sends correctly assembled DNS packets
- Stores packets using a robust object-oriented model
- Supports configuration via configuration files or command-line parameters
- Forwards DNS queries to upstream name servers using UDP
- Generates standards-compliant SERVFAIL responses on network errors
- Supports multiple load-balancing and redundancy strategies
- Utilizes a thread-safe configuration model with randomized retry jitter

## Project Structure

```text
src
└── main
    └── java
        └── io
            └── gitlab
                └── teamproject2026
                    ├── cache
                    │   ├── CacheConnectionProvider.java
                    │   └── CacheHandler.java
                    ├── config
                    │   ├── Config.java
                    │   ├── ConfigManager.java
                    │   ├── ConfLexer.java
                    │   ├── ConfLoader.java
                    │   ├── ConfNode.java
                    │   ├── ConfParser.java
                    │   ├── ConfToken.java
                    │   ├── ConfTokenType.java
                    │   ├── OptionConfig.java
                    │   ├── ZoneConfig.java
                    │   └── ZoneType.java
                    ├── data
                    │   └── MockResponses.java
                    ├── logging
                    │   ├── DNSEventDebug.java
                    │   ├── DNSEventInfo.java
                    │   ├── DNSEventType.java
                    │   └── LoggingFeature.java
                    ├── model
                    │   ├── DNSPacket.java
                    │   ├── OpCode.java
                    │   ├── QuestionSection.java
                    │   ├── RCode.java
                    │   ├── ResourceRecord.java
                    │   ├── RRClass.java
                    │   ├── RRType.java
                    │   └── rdata
                    │       ├── RData.java
                    │       ├── RDataA.java
                    │       ├── RDataAAAA.java
                    │       ├── RDataCNAME.java
                    │       ├── RDataMX.java
                    │       ├── RDataNS.java
                    │       ├── RDataPTR.java
                    │       ├── RDataSOA.java
                    │       └── RDataTXT.java
                    ├── zones
                    │   ├── ZoneLexer.java
                    │   ├── ZoneParser.java
                    │   ├── ZoneToken.java
                    │   └── ZoneTokenType.java
                    ├── DNS_SERVER.java
                    ├── DNSParser.java
                    ├── DNSZone.java
                    └── StubResolver.java
```

### Package Overview

| Package        | Purpose                                    |
|----------------|--------------------------------------------|
| `cache`        | Database-backed DNS cache implementation   |
| `config`       | `named.conf` lexing, parsing and loading   | 
| `data`         | Static mock DNS response data              |
| `logging`      | DNS event logging infrastructure           |
| `model`        | DNS packet, record, and protocol models    |
| `model.rdata`  | Resource record data implementations       |
| `zones`        | DNS zone-file lexer and parser (BIND9)     |
| `DNS_SERVER`   | Main server entry point                    |
| `DNSParser`    | DNS packet parsing and serialization       |
| `DNSZone`      | Parsed DNS zone, contains zone records     |
| `StubResolver` | Upstream DNS query forwarding and failover |

 
## Requirements
- Java 25
- Docker CLI tools (including Docker Compose) or Docker Desktop

## How to start
1. Compile the project with 
```bash
mvn compile
```

2. Start the PostgreSQL server using either
```bash
docker compose up -d
``` 
or use Docker Desktop, or generate an equivalent `docker run` command from `compose.yaml` using  [this](https://www.decomposerize.com/)  website. <br><br>
If there were changes to the database scheme since you last started the server, remove them so the init scripts can rerun:
```bash
docker compose down -v
```
3. Start the DNS server with 
```bash
mvn exec:java -Dexec.mainClass="io.gitlab.teamproject2026.DNS_SERVER"
```

### Unix
Alternatively, add an alias of your choice
```bash
alias run='mvn exec:java -Dexec.mainClass="io.gitlab.teamproject2026.DNS_SERVER"'
```
You can then start the server using the alias and optionally pass command-line parameters:
```bash
run -Dexec.args="-p 2222"
```
### Windows
Alternatively, add a function shortcut 
```PowerShell
function run { mvn exec:java -Dexec.mainClass="io.gitlab.teamproject2026.DNS_SERVER"}
```

You can then start the server using the shortcut and optionally pass command-line parameters:
```PowerShell
run -Dexec.args="-p 2222"
```

## Testing

The current project is tested using both black-box tests executed via a Bash script and unit tests.

### Unit Tests

| Test                       | Description                                                |
|----------------------------|------------------------------------------------------------|
| testSuccessfulResolution   | Verifies successful DNS resolution and packet integrity    |
| testTimeoutAndServFail     | Ensures timeouts generate RFC-compliant SERVFAIL responses |
| testFailoverSwitchesServer | Verifies automatic failover to backup servers              |
| testRoundRobinStrategy     | Verifies round-robin load balancing                        |

### Black-box testing

The CI will run the script test_server.sh. This script contains some black-box tests for the DNS server. 
- The `testCache` function is an unused artifact and should not be used in CI, as the cache can only be provided locally. It may therefore be used for local testing.

> **Note**
>
> If the CI pipeline fails without any apparent changes to `.gitlab-ci.yml` or `test_server.sh`, the server startup or compilation time may have exceeded the timeout configured in the test script. In this case, increase the timeout value accordingly.

### Examples on manual tests for the current state of the project
To test packet parsing and hexdump generation: `dig @127.0.0.1 uni-tuebingen.de +noedns AAAA`

To test supported DNS record types:
- `dig @127.0.0.1 uni-tuebingen.de +noedns A`
- `dig @127.0.0.1 uni-tuebingen.de +noedns AAAA`
- `dig @127.0.0.1 uni-tuebingen.de +noedns CNAME`
- `dig @127.0.0.1 uni-tuebingen.de +noedns SOA`
- `dig @127.0.0.1 uni-tuebingen.de +noedns TXT`
- `dig @127.0.0.1 uni-tuebingen.de +noedns NS`
- `dig @127.0.0.1 uni-tuebingen.de +noedns PTR`
- `dig @127.0.0.1 uni-tuebingen.de +noedns MX`


### Monitoring

Along with the database, the `compose.yaml` file also starts a GUI for the database. This GUI can be accessed [here](http://localhost:8080). To log in you must select PostgreSQL as the database server type. 

Use the following credentials to log in:

| Setting  | Value     |
|----------|-----------|
| Username | postgres  |
| Password | example   |
| Database | dns       |

# Configuration parameters

## Command line
The parameter `-p` is used in the command line to specify a custom port. 
```bash
mvn exec:java -Dexec.mainClass="io.gitlab.teamproject2026.DNS_SERVER" -Dexec.args="-p 2222"
```

The parameter `-a` is used to specify a custom IP address.
```bash
mvn exec:java -Dexec.mainClass="io.gitlab.teamproject2026.DNS_SERVER" -Dexec.args="-a 0.0.0.0"
```

The parameter `--testing` is used to enable test mode, which disables accessing of external resources which cause errors in the CI pipeline.
```bash
mvn exec:java -Dexec.mainClass="io.gitlab.teamproject2026.DNS_SERVER" -Dexec.args="--testing"
``` 
Internally, this sets a parameter in the `DNS_SERVER` class and can be accessed by calling the `getTestingStatus` function.

## DNS server config file (`named.conf`)
The DNS server supports a BIND-style configuration file called `named.conf`.



If a file called `named.conf` exists in the server’s working directory, it can be used to configure default server settings such as the listening address, port, and zone definitions. Command-line arguments take precedence over values defined in the configuration file.
Example config;


```aiignore
# DNS server configuration
# These values are overridden by command-line arguments if provided.
options {
    directory "zones";
    listen-on port 2222 {127.0.0.1; };
};
```
This configuration instructs the server to listen on 127.0.0.1:2222 and to use the zones directory as the default location for zone files.

The primary purpose of `named.conf` is to declare DNS zones that should be loaded when the server starts.

```aiignore
options {
    directory "zones";
    listen-on port 2222 {127.0.0.1; };
    listen-on-v6 { none; };
};

zone "example.com" {
    type primary;
    file "db.example.com";
};

zone "0.0.129.in-addr-arpa" {
    type primary;
    file "db.129.0.0";
};
```

Two top-level declarations are supported:

- options – server-wide configuration settings
- zone – DNS zone definitions

A zone declaration specifies:

1. The zone origin (for example `"example.com"`)
2. The zone type
3. The zone file to load

The zone types primary and master are treated as equivalent and indicate that this server is authoritative for the zone.

The types secondary and slave indicate that the zone is managed by another server and would normally be synchronized through DNS zone transfers. Since this project does not implement zone transfers, only primary zones should be used.


The file directive specifies the zone file that should be loaded. If the file path is relative (does not begin with `'/'`), it is resolved relative to the directory specified by the directory option.


For example:
```aiignore
options {
    directory "zones";
};

zone "example.com" {
    type primary;
    file "db.example.com";
};
```

causes the server to load:
```aiignore
zones/db.example.com
```

## DNS Zone Files

Zone data is stored in standard BIND-style zone files. Each zone declared in named.conf references a zone file containing the resource records for that zone.

Example:
```dns
$ORIGIN example.com. 
$TTL 3600  

@ IN SOA ns1.example.com. admin.example.com. (
     2026051201     
     3600     
     1800     
     1209600     
     3600 
)  

@       IN NS     ns1.example.com. 
ns1     IN A      192.0.2.53 
www     IN A      192.0.2.1 
mail    IN A      192.0.2.2  
@       IN MX     10 mail.example.com. 
@       IN TXT    "Example DNS zone"
```


### Supported Directives

#### $ORIGIN

Sets the current zone origin. Relative domain names are automatically expanded using the active origin.

Example:

```dns
$ORIGIN example.com.  www IN A 192.0.2.1
```

is interpreted as:

```dns
www.example.com. IN A 192.0.2.1
```

---

#### $TTL

Defines the default TTL (Time To Live) used by records that do not explicitly specify a TTL.

Example:

```dns
$TTL 3600  www IN A 192.0.2.1
```


The record will receive a TTL of 3600 seconds.

TTL values may also use DNS duration suffixes:

| Suffix | Meaning |
|--------|---------|
| s      | seconds |
| m      | minutes |
| h      | hours   |
| d      | days    |
| w      | weeks   |

Example:
```dns
$TTL 1h  
www 30m IN A 192.0.2.1
```

---

#### $INCLUDE

Includes another zone file at the current location.

Example:

```dns
$ORIGIN example.com.
@ IN SOA ns1.example.com. admin.example.com. (
     2026051201     
     3600     
     1800     
     1209600     
     3600 
)  
@ IN NS ns1.example.com.  

$INCLUDE "subdomain.zone"  optional.new.origin

www IN A 192.0.2.1
```

If subdomain.zone contains:
```dns
api  IN A 192.0.2.10 
mail IN A 192.0.2.20
```

The parser behaves as if the included records were inserted directly at the location of the `$INCLUDE` directive.

Included files inherit the currently active `$ORIGIN` and `$TTL` values unless an optional new origin is provided. If a new origin is specified, it is used only while parsing the included file. After the include has been processed, the parent zone file continues using its original origin.

If no `$ORIGIN` directive exists in the zone file, the origin specified in `named.conf` is used.

Relative include paths are resolved relative to the zone directory configured in `named.conf`.

#### Restrictions

A zone must contain exactly one SOA record.

Included files must therefore not introduce an additional SOA record. Loading a zone that contains multiple SOA records will fail validation.

### Comments

Comments begin with a semicolon (`;`) and continue until the end of the line.

Example:

```dns
www IN A 192.0.2.1 ; Web server
```

### Multiline Records

Parentheses may be used to split records across multiple lines.

This is commonly used for SOA records and large TXT records.

Example:

```dns
@ IN SOA ns1.example.com. admin.example.com. (
     2026051201     
     3600     
     1800     
     1209600     
     3600 
)
```

TXT records may also span multiple quoted strings:

```dns
multitxt IN TXT (
     "This text "     
     "spans multiple "     
     "strings" 
)
```

### Escaping Special Characters

Domain labels and TXT strings support DNS escape sequences.

Examples:
```dns
foo\.bar      IN A   192.0.2.1 
literal\@     IN TXT "hello" 
space\ label  IN TXT "world"
```

Decimal escape sequences are also supported:
```dns
hello\032world IN TXT "example"
foo\046bar     IN A   192.0.2.1
```

which correspond to:

```text
\032 = space 
\046 = . 
\064 = @ 
\092 = \
```


## Stub Resolver Configuration
The stub resolver can be configured in the `options` section of `named.conf`.

If a setting is not specified, a built-in default value is used.

Example configuration:

```conf
options {
    # Upstream DNS servers used for forwarding queries
    forwarders {
        8.8.8.8;
        1.1.1.1;
    };

    # Forwarding mode
    forward only;

    # Resolver tuning options
    resolver {
        timeout 2000;
        max-retries 2;
        retry-delay 100;
        strategy failover;
    };
};
```

### Forwarders

The forwarders section defines one or more upstream DNS servers to which queries are forwarded.

```conf
forwarders {
    8.8.8.8;
    1.1.1.1;
};
```
If no forwarders are configured, the default servers are used.

### Forwarding mode

The forward directive controls how queries are resolved.

```conf
forward only;
```

- only - Queries are sent exclusively to the configured forwarders.
- first - Queries are sent to the configured forwarders first. If all forwarders fail, the server performs recursive resolution itself.

### Resolver options

The optional resolver section controls internal resolver behavior.

```conf
resolver {
    timeout 2000;
    max-retries 2;
    retry-delay 100;
    strategy failover;
};
```

#### timeout

Specifies the timeout for a forwarded query in milliseconds.

```conf
timeout 2000;
```

#### max-retries

Specifies the maximum number of retry attempts before a query is considered failed.

```conf
max-retries 2;
```

#### retry-delay

Specifies the delay between retry attempts in milliseconds.

```conf
retry-delay 100;
```

#### strategy

Specifies how upstream servers are selected.

Supported values:

- `failover` - Try forwarders in the order they are configured until one responds.
- `round-robin` - Rotate through the configured forwarders.
- `random` - Select a random forwarder for each query.
- `parallel` - Query all forwarders simultaneously and use the first response.

## Database config files
You can change the configuration of the PostgreSQL server both in the `compose.yaml` and the `postgres/postgres.conf` files.  
- `compose.yaml` determines how the Docker container is created and initialised. For example, it configures the container such that, the `postgres.conf`  file is mounted in the container to `/etc/postgresql/postgresql.conf` directory and used as the main config file for the PostgreSQL server. 
- `postgres.conf` is the config file for the PostgreSQL server itself

Finally, you can configure the initial state of the database by mounting scripts to the `/docker-entrypoint-initdb.d/` directory within the container, as any `.sql` or `.sh` scripts will automatically be executed on container startup. 


## Known issues and bugs
- None

# Changelog
```diff
~ config/
~ zones/
~ DNSZone
~ DNS_SERVER
```
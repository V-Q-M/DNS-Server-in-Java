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
                    ├── DNS_SERVER.java
                    ├── DNSParser.java
                    └── StubResolver.java
```

### Package Overview

| Package        | Purpose                                    |
|----------------|--------------------------------------------|
| `cache`        | Database-backed DNS cache implementation   |
| `data`         | Static mock DNS response data              |
| `logging`      | DNS event logging infrastructure           |
| `model`        | DNS packet, record, and protocol models    |
| `model.rdata`  | Resource record data implementations       |
| `DNS_SERVER`   | Main server entry point                    |
| `DNSParser`    | DNS packet parsing and serialization       |
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
or use Docker Desktop, or generate an equivalent `docker run` command from `compose.yaml` using  [this](https://www.decomposerize.com/)  website.

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

## Configuration parameters

### Command line
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

### DNS server config file
If a file named `config.properties` exists, it can be used to define a new default port and IP address.

Example config;
```aiignore
# DNS server configuration
# These values are overridden by command-line arguments if provided.
server.port=1337
server.ip=127.0.0.1
```
This example specifies a default port `1337` and default IP address `127.0.0.1`.

If no config file exists and no command line arguments are provided, the fallback port `4242` and fallback IP address `0.0.0.0` is used.

### StubResolver config file
The default upstream name servers used for routing can be configured in `config.properties`.

Example StubResolver configuration:
```aiignore
# Stub Resolver Upstream configuration
resolver.timeout=1000
resolver.max_retries=2
resolver.strategy=FAILOVER
```

### Database config files
You can change the configuration of the PostgreSQL server both in the `compose.yaml` and the `postgres/postgres.conf` files.  
- `compose.yaml` determines how the Docker container is created and initialised. For example, it configures the container such that, the `postgres.conf`  file is mounted in the container to `/etc/postgresql/postgresql.conf` directory and used as the main config file for the PostgreSQL server. 
- `postgres.conf` is the config file for the PostgreSQL server itself

Finally, you can configure the initial state of the database by mounting scripts to the `/docker-entrypoint-initdb.d/` directory within the container, as any `.sql` or `.sh` scripts will automatically be executed on container startup. 


## Known issues and bugs
- TXT Records can currently only be parsed if they contain only one string. 

# Changelog
```diff
~ README.md
```
# Teamproject **Implementing a DNS server**

## Group members
### Group 1
- Hölz, Robin, 6979700, robin.hoelz@student.uni-tuebingen.de
- Müller, Viktor, 6349273, viktor.mueller@student.uni-tuebingen.de
- Philippin, Johannes, 6994772, johannes.philippin@student.uni-tuebingen.de
- Ruoff, Laurenz, 6978381, laurenz.ruoff@student.uni-tuebingen.de
- Bobes, Anas, 6688712, Anas.Bobes@student.uni-tuebingen.de
### Group 2
- Name, Firstname, Mat-Nr, Email

## About
- Forward DNS queries to upstrean nameservers using UDP packets
- Handle network timeouts and I/O errors by generating standard-compliant SERVFAIL response
- Support multiple load balancing and redundancy strategy
- Utilize a thread-safe configuration model with randomized jitter delays for retries

## Requirements
The project uses Java 25, which can be downloaded from the internet.

## How to start
First, compile the project with 
```
mvn compile
```

Then start the the server with 
```
java -cp target/classes io.gitlab.teamproject2026.DNS_SERVER 
```

### Unix
Alternatively, add an alias of your choice
```bash
alias run=java -cp target/classes io.gitlab.teamproject2026.DNS_SERVER
```
And start the server with your alias (and parameters)
```bash
run -p 2222
```
### Windows
Alternatively, add a function shortcut 
```PowerShell
function run { java -cp target/classes io.gitlab.teamproject2026.DNS_SERVER $args }
```

And start the server with your alias (and parameters)
```PowerShell
run -p 2222
```
###Testing
`testSuccessfulResolution`Überprüft, ob gültige DNS-Pakete korrekt gesendet und Antworten ohne Datenverlust verarbeitet werden.
`testTimoutAndServFail`Simuliert eine Netzwerkzögerung. Es wird überprüft, ob der Resolver die Anfrage rechtzeitig abbricht und korrekt DNS-Paket generiert.
`testFailoverSwitchesServer`Testet das Verhalten bei mehreren Servern.
`testRoundRobinStrategy`Überprüft die zyklische Verteilung der Anfragen auf die Nameserver zur Lastverteilung.



### Examples on tests for the current state of the project

Testing hexdump and valid parsing:  `dig @127.0.0.1 uni-tuebingen.de +noedns AAAA`

Testing valid response packets:
- `dig @127.0.0.1 uni-tuebingen.de +noedns A`
- `dig @127.0.0.1 uni-tuebingen.de +noedns AAAA`
- `dig @127.0.0.1 uni-tuebingen.de +noedns CNAME`
- `dig @127.0.0.1 uni-tuebingen.de +noedns SOA`
- `dig @127.0.0.1 uni-tuebingen.de +noedns TXT`
- `dig @127.0.0.1 uni-tuebingen.de +noedns NS`
- `dig @127.0.0.1 uni-tuebingen.de +noedns PTR`
- `dig @127.0.0.1 uni-tuebingen.de +noedns MX`


## Configuration parameters
Die Funktionsweiswe des Stub Resolver wird initial beim Erstellen des Objekt übergeben.

### Command line
The parameter `-p` is used in the command line to specify a custom port. 
```bash
java -cp target/classes io.gitlab.teamproject2026.DNS_SERVER -p 2222
```

The parameter `-a` is used to specify a custom IP-Address.
```bash
java -cp target/classes io.gitlab.teamproject2026.DNS_SERVER -a 0.0.0.0
```


### Config file
In der `config.properties` können die standard Nameserver für Routing hinterlegt werden.

Beispiel für den StubResolver;
```aiignore
# Stub Resolver Upstream configuration
resolver.timeout=1000
resolver.max_retries=2
resolver.strategy=FAILOVER
```


## Known issues and bugs
- 

# Changelog
```diff
~ DNS_SERVER.java
~ StubResolver.java
~ StubResolverTest.java
~ config.properties
```

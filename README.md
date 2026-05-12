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
- Receive and send correctly assembled DNS packets (with mock responses)
- Store the packets in a robust OOP model
- Use a Config file or command line parameters to configure the server
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
If a file named `config.properties` exists, it can be used to define a new default port and IP-Address.

Example config;
```aiignore
# DNS Server Configuration
# These values are overridden by command-line arguments if provided.
server.port=1337
server.ip=127.0.0.1
```
This example specifies a default port `1337` and default IP-Address `127.0.0.1`.

If no config file exists and no command line arguments are provided, the fallback port `4242` and fallback IP-Address `0.0.0.0` is used.


## Known issues and bugs
- It is unclear wether port numbers below `1024` are supposed to be supported. Allowing them would require `sudo` rights and risk a crash, should the necessary rights not be provided.

# Changelog
```diff
~ DNS_SERVER.java
~ DNSParser.java and fromBytes methods
```

# Teamproject **Implementing a DNS server**

## Group members
*List of members in this project and their group. Please insert your information in this list*
### Group 1
- Name, Firstname, Mat-Nr, Email
### Group 2
- Name, Firstname, Mat-Nr, Email

## About
*Current set of features for this project. Extend this after each week.*

## Requirements
The project uses Java 25, which can be downloaded from the internet.

## How to start
First, compile the project with 
```
mvn compile
```

Then start the the server with 
```
java -cp target/classes io.gitlab.teamproject2026.DnsServer 
```

Or with a custom port number
```
java -cp target/classes io.gitlab.teamproject2026.DnsServer -p 2222
```

### Examples on tests for the current state of the project

Testing hexdump:  `dig @127.0.0.1 uni-tuebingen.de AAAA`


## Configuration parameters
The `-p` is used in the command line to specift a custom port. 
```
java -cp target/classes io.gitlab.teamproject2026.DnsServer -p 2222
```
If no custom port is specified, the default port of 4242 is used.

## Known issues and bugs
None as of yet

# Changelog
```diff
~ DNS_SERVER.java
```

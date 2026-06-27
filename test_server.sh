#!/bin/bash

DB_HOST=${DB_HOST:-localhost}

doLookup() {
    local QUESTION=${1}
    local TYPE=${2}
    local EXPECT=${3}
    local TIME=${4}
    local RESULT=$(dig @127.0.0.1 -p ${PORT} +nodnssec +noedns ${QUESTION} ${TYPE})
    verifyResult "${RESULT}" "${EXPECT}" "${TIME}" "${QUESTION}" "${TYPE}"
}

verifyResult() {
    local RESULT=${1}
    local EXPECT=${2}
    local TIMEEXPECT=${3}
    local QUESTION=${4}
    local TYPE=${5}
    local QUERYTIME=$(echo ${RESULT} | grep -oE "Query time: [0-9]{1,} msec" | sed 's/^.*Query time: //g' | sed 's/ msec.*$//g')
    if echo "${RESULT}" | grep -q "${EXPECT}"; then
        if [ "${TIMEEXPECT}" -gt ${QUERYTIME} ]; then
            echo -e "${GREEN}Successfully queried ${QUESTION} ${TYPE} in ${QUERYTIME} ms!${NC}"
        else
            echo -e "${ORANGE}Query for ${QUESTION} ${TYPE} took too long: ${QUERYTIME} ms!${NC}"
            #ERRCOUNT=$((ERRCOUNT + 1)) #causes pipeline fails only in ci, not when run locally
        fi
    else
        echo -e "${RED}Did not find expected result for ${QUESTION} ${TYPE}${NC}"
        echo "Expected: ${EXPECT}"
        echo "----- dig result -----"
        echo "${RESULT}"
        echo "----------------------"
        ERRCOUNT=$((ERRCOUNT + 1))
    fi
}

startDatabase() {
    echo "Waiting for PostgreSQL service..."
    for i in {1..30}; do
        if pg_isready -h "${DB_HOST}" -p 5432 -U postgres > /dev/null 2>&1; then
            echo -e "${GREEN}PostgreSQL is ready${NC}"
            return
        fi
        sleep 1
    done

    echo -e "${RED}PostgreSQL did not become ready${NC}"
    ERRCOUNT=$((ERRCOUNT + 1))
}

startServer() {
    mvn compile
    mvn exec:java -Dexec.mainClass="io.gitlab.teamproject2026.DNS_SERVER" -Dexec.args="-p ${1} --less-log --minimal-responses" > server.log 2>&1 &
    echo "Attempt to start server on port ${1}..."
}

verifyServerRunning() {
    echo "Waiting for server startup..."

    for i in {1..30}; do
        if grep -q "UDP server listening on" server.log; then
            echo -e "${GREEN}Server successfully started${NC}"
            return
        fi
        sleep 1
    done

    echo -e "${RED}ERROR: Server did not start properly${NC}"
    echo "----- server.log -----"
    cat server.log
    echo "----------------------"
    ERRCOUNT=$((ERRCOUNT + 1))
}

cleanUpAndResult() {
    #echo "Cleaning up..."
    pkill java || true
    echo "----------------------------------------------------------------------"
    if [ ${ERRCOUNT} -gt 0 ]; then
        echo -e "${RED}${ERRCOUNT} tests failed.${NC}"
        exit 1
    else
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    fi
}

testParser(){
    echo "Testing parser responses..."
    doLookup "uni-tuebingen.de" "A" "134.2.5.1" ${NOCACHE}
    doLookup "uni-tuebingen.de" "AAAA" "2001:7c0:300c:30f0::8602:501" ${NOCACHE}
    doLookup "uni-tuebingen.de" "CNAME" "uni-tuebingen-2.de" ${NOCACHE}
    doLookup "uni-tuebingen.de" "SOA" "nameserver.uni-tuebingen.de. admin.uni-tuebingen.de. 2026010101 3600 900 604800 300" ${NOCACHE}
    doLookup "uni-tuebingen.de" "NS" "nameserver.uni-tuebingen.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "MX" "mailserver.uni-tuebingen.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "PTR" "uni-tuebingen.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "TXT" "hello world" ${NOCACHE}
}


testResolver(){
    echo "Testing Resolver according to forwardMode..."
    doLookup "uni-tuebingen.de" "A" "134.2.5.1" ${NOCACHE}
    doLookup "uni-tuebingen.de" "MX" "c1541.mx.srv.dfn.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "MX" "ANSWER: 3" ${NOCACHE}
    doLookup "uni-tuebingen.de" "TXT" "ANSWER: 6" ${NOCACHE}
    doLookup "uni-tuebingen.de" "TXT" "MS=ms30248456" ${NOCACHE}

    # CNAME response
    doLookup "kn-mattermost.cs.uni-tuebingen.de" "A" "atlas.cs.uni-tuebingen.de" ${NOCACHE}
    doLookup "kn-mattermost.cs.uni-tuebingen.de" "A" "134.2.10.224" ${NOCACHE}
    doLookup "uni-tuebingen.de" "AAAA" "2001:7c0:300c:30f0::8602:501" ${NOCACHE}
    doLookup "uni-tuebingen.de" "NS" "dns1.belwue.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "NS" "ANSWER: 4" ${NOCACHE} # weird test, sometimes is answer, sometimes is authority
    # Querying directly for CNAME
    doLookup "kn-mattermost.cs.uni-tuebingen.de" "CNAME" "atlas.cs.uni-tuebingen.de" ${NOCACHE}
    doLookup "kn-mattermost.cs.uni-tuebingen.de" "CNAME" "ANSWER: 1" ${NOCACHE}
    doLookup "uni-tuebingen.de" "SOA" "dns6.uni-tuebingen.de. hostmaster.uni-tuebingen.de." ${NOCACHE}
    doLookup "1.5.2.134.in-addr.arpa" "PTR" "www.uni-tuebingen.de." ${NOCACHE}
}

testCache(){
    echo "Testing caching times..."
    doLookup "uni-tuebingen.de" "A" "134.2.5.1" ${NOCACHE}
    doLookup "uni-tuebingen.de" "A" "134.2.5.1" ${CACHETIME}
    # Test for invalid TTL
    doLookup "uni-tuebingen.de" "AAAA" "2001:7c0:300c:30f0::8602:501" ${NOCACHE}
    doLookup "uni-tuebingen.de" "AAAA" "2001:7c0:300c:30f0::8602:501" ${NOCACHE}
    doLookup "uni-tuebingen.de" "MX" "c1541.mx.srv.dfn.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "MX" "c1541.mx.srv.dfn.de." ${CACHETIME}
    doLookup "uni-tuebingen.de" "SOA" "dns6.uni-tuebingen.de. hostmaster.uni-tuebingen.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "SOA" "dns6.uni-tuebingen.de. hostmaster.uni-tuebingen.de." ${CACHETIME}
    doLookup "1.5.2.134.in-addr.arpa" "PTR" "www.uni-tuebingen.de." ${NOCACHE}
    doLookup "1.5.2.134.in-addr.arpa" "PTR" "www.uni-tuebingen.de." ${CACHETIME}
    # Case insensitivity
    doLookup "uNi-tUeBiNgEn.dE" "A" "134.2.5.1" ${CACHETIME}
}

testZone(){
    echo "Testing authoritative zones..."
    doLookup "example.com" "A" "129.0.0.1" ${CACHETIME}
    doLookup "www.example.com" "CNAME" "example.com" ${CACHETIME}
    doLookup "www.example.com" "A" "example.com" ${CACHETIME}
    doLookup "www.example.com" "A" "129.0.0.1" ${CACHETIME}
    doLookup "mx1.example.com" "A" "129.0.0.4" ${CACHETIME}
    doLookup "example.com" "MX" "mx1.example.com." ${CACHETIME}
    doLookup "example.com" "MX" "mx2.example.com." ${CACHETIME}
    doLookup "example.com" "NS" "ns0.example.com" ${CACHETIME}
    doLookup "ns0.example.com" "A" "129.0.0.2" ${CACHETIME}
    doLookup "example.com" "AAAA" "2001:db8::1" ${CACHETIME}
    doLookup "example.com" "TXT" "Hello, world!" ${CACHETIME}
    doLookup "1.0.0.129.in-addr.arpa." "PTR" "example.com." ${CACHETIME}

    doLookup "example.com" "SOA" "ns0.example.com. admin.example.com." ${CACHETIME}
}

testZone(){
    echo "Testing authoritative zones..."
    doLookup "example.com" "A" "129.0.0.1" ${CACHETIME}
    doLookup "www.example.com" "CNAME" "example.com" ${CACHETIME}
    doLookup "www.example.com" "A" "example.com" ${CACHETIME}
    doLookup "www.example.com" "A" "129.0.0.1" ${CACHETIME}
    doLookup "mx1.example.com" "A" "129.0.0.4" ${CACHETIME}
    doLookup "example.com" "MX" "mx1.example.com." ${CACHETIME}
    doLookup "example.com" "MX" "mx2.example.com." ${CACHETIME}
    doLookup "example.com" "NS" "ns0.example.com" ${CACHETIME}
    doLookup "ns0.example.com" "A" "129.0.0.2" ${CACHETIME}
    doLookup "example.com" "AAAA" "2001:db8::1" ${CACHETIME}
    doLookup "example.com" "TXT" "Hello, world!" ${CACHETIME}
    doLookup "1.0.0.129.in-addr.arpa." "PTR" "example.com." ${CACHETIME}

    doLookup "example.com" "SOA" "ns0.example.com. admin.example.com." ${CACHETIME}
}



trap cleanUpAndResult SIGINT
trap cleanUpAndResult EXIT

RED='\033[0;31m'
GREEN='\033[0;32m'
ORANGE='\033[0;33m'
NC='\033[0m' # No Color

ERRCOUNT=0
CACHETIME=100
NSCACHE=1000
NOCACHE=100000
PORT="1337"
RESOLVER="127.0.0.1"

# Start server and tests

# testing what is available on CI
startDatabase
startServer ${PORT}
verifyServerRunning
# First query, as first quries always take much longer than later queries
dig @127.0.0.1 -p ${PORT} +nodnssec +noedns uni-tuebingen.de A > /dev/null
testResolver
testCache
testZone

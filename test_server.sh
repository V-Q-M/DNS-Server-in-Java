#!/bin/bash

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
        if [ ${TIMEEXPECT} -ge ${QUERYTIME} ]; then
            echo -e "${GREEN}Successfully queried ${QUESTION} ${TYPE} in ${QUERYTIME} ms!${NC}"
        else
            echo -e "${ORANGE}Query for ${QUESTION} ${TYPE} took too long: ${QUERYTIME} ms!${NC}"
            ERRCOUNT=$((ERRCOUNT + 1))
        fi
    else
        echo -e "${RED}Did not find expected result for ${QUESTION} ${TYPE}${NC}"
        ERRCOUNT=$((ERRCOUNT + 1))
    fi
}

startServer() {
    java -jar target/Teamprojekt-1.0-SNAPSHOT.jar -p ${1} -a ${2} &
    echo "Attempt to start server on port ${1}..."
}

verifyServerRunning() {
    sleep 2
    if ps aux | grep -v grep | grep  "Teamprojekt-1.0-SNAPSHOT.jar"; then
        echo -e "${GREEN}Server successfully started${NC}"
    else
        echo -e "${RED}ERROR: Server did not start properly${NC}"
        ERRCOUNT=$((ERRCOUNT + 1))
    fi
}

cleanUpAndResult() {
    echo "Cleaning up..."
    pkill -f Teamprojekt-1.0-SNAPSHOT.jar 2>/dev/null
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
    doLookup "uni-tuebingen.de" "A" "127.0.0.1" ${NOCACHE}
    doLookup "uni-tuebingen.de" "AAAA" "::1" ${NOCACHE}
    doLookup "uni-tuebingen.de" "CNAME" "e.com" ${NOCACHE}
    doLookup "uni-tuebingen.de" "SOA" "ns1.e.com. a.e.com. 12345 900 900 1800 60" ${NOCACHE}
    doLookup "uni-tuebingen.de" "NS" "tld1.e.com." ${NOCACHE}
    doLookup "uni-tuebingen.de" "NS" "tld2.e.com" ${NOCACHE}
    doLookup "google.de" "NS" "ANSWER: 2" ${NOCACHE}
    doLookup "uni-tuebingen.de" "MX" "smtp.e.com." ${NOCACHE}
    doLookup "uni-tuebingen.de" "PTR" "ptr.e.com." ${NOCACHE}
    doLookup "uni-tuebingen.de" "TXT" "Hello World!" ${NOCACHE}
}


testStubResolver(){
    echo "Testing Stub Resolver..."
    doLookup "uni-tuebingen.de" "A" "134.2.5.1" ${NOCACHE}
    doLookup "uni-tuebingen.de" "MX" "c1541.mx.srv.dfn.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "MX" "ANSWER: 3" ${NOCACHE}
    doLookup "uni-tuebingen.de" "TXT" "ANSWER: 5" ${NOCACHE}
    doLookup "uni-tuebingen.de" "TXT" "MS=ms30248456" ${NOCACHE}
    # CNAME response
    doLookup "kn-mattermost.cs.uni-tuebingen.de" "A" "atlas.informatik.uni-tuebingen.de" ${NOCACHE}
    doLookup "kn-mattermost.cs.uni-tuebingen.de" "A" "134.2.14.130" ${NOCACHE}
    doLookup "uni-tuebingen.de" "AAAA" "2001:7c0:300c:30f0::8602:501" ${NOCACHE}
    doLookup "uni-tuebingen.de" "NS" "dns1.belwue.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "NS" "ANSWER: 4" ${NOCACHE}
    # Querying directly for CNAME
    doLookup "kn-mattermost.cs.uni-tuebingen.de" "CNAME" "atlas.informatik.uni-tuebingen.de" ${NOCACHE}
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
    doLookup "uni-tuebingen.de" "TXT" "MS=ms30248456" ${NOCACHE}
    doLookup "uni-tuebingen.de" "TXT" "MS=ms30248456" ${CACHETIME}
    doLookup "uni-tuebingen.de" "SOA" "dns6.uni-tuebingen.de. hostmaster.uni-tuebingen.de." ${NOCACHE}
    doLookup "uni-tuebingen.de" "SOA" "dns6.uni-tuebingen.de. hostmaster.uni-tuebingen.de." ${CACHETIME}
    doLookup "1.5.2.134.in-addr.arpa" "PTR" "www.uni-tuebingen.de." ${NOCACHE}
    doLookup "1.5.2.134.in-addr.arpa" "PTR" "www.uni-tuebingen.de." ${CACHETIME}
    # Case insensitivity
    doLookup "uNi-tUeBiNgEn.dE" "A" "134.2.5.1" ${CACHETIME}
}


trap cleanUpAndResult SIGINT
trap cleanUpAndResult EXIT

RED='\033[0;31m'
GREEN='\033[0;32m'
ORANGE='\033[0;33m'
NC='\033[0m' # No Color

ERRCOUNT=0
CACHETIME=15
NSCACHE=300
NOCACHE=30
PORT="1337"
RESOLVER="127.0.0.1"

# Start server and tests
startServer ${PORT} ${RESOLVER}
verifyServerRunning
testStubResolver
testCache

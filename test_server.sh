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
    mvn compile && java -cp target/classes io.gitlab.teamproject2026.DNS_SERVER -p ${1} --testing > /dev/null 2>&1 & echo "Attempt to start server on port ${1}..."
}

verifyServerRunning() {
    sleep 2
    if ps xa | grep "java -cp target/classes io.gitlab.teamproject2026.DNS_SERVER -p ${1}" | grep -v "grep"; then
        echo -e "${GREEN}Server successfully started${NC}"
    else
        echo -e "${RED}ERROR: Server did not start properly${NC}" 
        ERRCOUNT=$((ERRCOUNT + 1))
    fi
}

cleanUpAndResult() {
    #echo "Cleaning up..."
    pkill java
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
startServer ${PORT}
verifyServerRunning
testParser
# removing testCache as our Cache implementation cannot be tested in CI
# testCache 

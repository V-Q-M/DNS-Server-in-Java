$TTL 4200

@       IN SOA  ns0.example.com. admin.example.com. (
                2026060209
                2000
                1800
                1209600
                86400
)

@       IN NS   ns0.example.com.

@       IN A    129.0.0.1
@       IN AAAA 2001:db8::1

$INCLUDE "example-records.inc" example.com.
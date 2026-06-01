; db.example.com

$ORIGIN example.com.
$TTL 3600

@   IN SOA ns1.example.com. admin.example.com. (
        1
        3600
        900
        604800
        86400
)

@       IN NS    ns1.example.com.
ns1     IN A     192.168.1.10

$INCLUDE sub.zone sub.example.com.

www     IN A     192.168.1.20
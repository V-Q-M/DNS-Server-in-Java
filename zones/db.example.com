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

; Address records for service hosts
ns0     IN A    129.0.0.2
mx1     IN A    129.0.0.4
mx2     IN A    129.0.0.5

; Mail exchangers
@       IN MX   10 mx1.example.com.
@       IN MX   20 mx2.example.com.

; Alias
www     IN CNAME example.com.

; TXT record
@       IN TXT  "Hello, world!"

; Example zone reusable records (safe include snippet)

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
@       IN TXT  "v=spf1 ip4:129.0.0.1 -all"
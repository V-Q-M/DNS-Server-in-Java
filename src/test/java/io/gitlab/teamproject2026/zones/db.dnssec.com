; File written on Fri May 15 03:11:04 2026
; dnssec-signzone version 9.20.22
$ORIGIN example.com.
$TTL 3600

example.com.		3600	IN SOA	ns1.example.com. hostmaster.example.com. (
					2026051501 ; serial
					3600       ; refresh (1 hour)
					900        ; retry (15 minutes)
					1209600    ; expire (2 weeks)
					3600       ; minimum (1 hour)
					)
			3600	RRSIG	SOA 13 2 3600 (
					20260614001104 20260515001104 59077 example.com.
					xaNlPQjHKG0Pdne0qooZ+vkvzPZKbX1oe3gp
					9djlIgl3V0NqcNdHQqe6Ha5mJ5DGwvCjZx92
					4kywVjIxT8y2Aw== )
			3600	NS	ns1.example.com.
			3600	RRSIG	NS 13 2 3600 (
					20260614001104 20260515001104 59077 example.com.
					ai4UkfcNDH3LUmTRMbeeOb47RW1yy83wvbEA
					pZmjqixZzxx3VPmFpd84WoZYLo3XCNScSSQ/
					W32dx4ncF7G8Aw== )
			3600	NSEC	blog.example.com. NS SOA RRSIG NSEC DNSKEY
			3600	RRSIG	NSEC 13 2 3600 (
					20260614001104 20260515001104 59077 example.com.
					aM5/EuSORiYeMx0G2Ru4OiF654hXWWZXyHVl
					rLzbQ5mysgoID3CPSm4iMR7yjYK1FINvnvmQ
					fxPGT4MkeU6ZHQ== )
			3600	DNSKEY	256 3 13 (
					odcfrx8+jilvyqeh0VA/qiZSix4ZMFUeDCMZ
					LNJb4Tdy9sn7nUJ1IxfMrFBnACgk1rO/FHTn
					TVkIF65fzSPUgw==
					) ; ZSK; alg = ECDSAP256SHA256 ; key id = 59077
			3600	DNSKEY	257 3 13 (
					GaE3OkE7BilmhvkWda2O9YfuSVeniA4ahz++
					D88UEevLoYXWdZC0qFXInpegDz2OBizwFa9T
					Hi8kC/1yrtPieQ==
					) ; KSK; alg = ECDSAP256SHA256 ; key id = 29189
			3600	RRSIG	DNSKEY 13 2 3600 (
					20260614001104 20260515001104 29189 example.com.
					BLVgJUgc4X9Thv9hlrRj5trnc3Y1YaPyZZl6
					8ivvU0kkQtalpPeG64ZEaEooOEgnZKSt9fs/
					3e6FdS8xYev/iw== )
			3600	RRSIG	DNSKEY 13 2 3600 (
					20260614001104 20260515001104 59077 example.com.
					+uZmd9sYH5V9z3hxxGPyri64CiGdY31h4cDl
					kmBaPulWb1y34ISsLrBnZqxNQus+bJ8ofyuf
					IQ3QbDmr3UHn/g== )
blog.example.com.	3600	IN CNAME www.example.com.
			3600	RRSIG	CNAME 13 3 3600 (
					20260614001104 20260515001104 59077 example.com.
					5phG7R/f5F03KISHQBLhpYzA0ovh810ChA9/
					YZXCOMlCz3n6LfnVFvOp/79ga5jD/gmdkPXi
					QKXoo9KCrHmaMg== )
			3600	NSEC	ns1.example.com. CNAME RRSIG NSEC
			3600	RRSIG	NSEC 13 3 3600 (
					20260614001104 20260515001104 59077 example.com.
					NCM7D2nPgHBW3+xgJkqKGp+8cZhcP4TdXjnq
					zdm8ILUlEl/A8A5Y2OdmyPGNLCfZT4/G7uTC
					BwdVdutTJAAGJQ== )
ns1.example.com.	3600	IN A	192.0.2.10
			3600	RRSIG	A 13 3 3600 (
					20260614001104 20260515001104 59077 example.com.
					Md2fvCqlThEBVOvbvtPhzW9EREFrFOvzTDGl
					lzgWReDHvZ3ZexfxMbmd/3BSnUbAf6mvu7jL
					Q5ahoJqwe2UK4g== )
			3600	NSEC	www.example.com. A RRSIG NSEC
			3600	RRSIG	NSEC 13 3 3600 (
					20260614001104 20260515001104 59077 example.com.
					gaDcP0fZsYkImrdZUuOj90tnnyDJe82pWGNC
					/U3o94XgXeHobgatjacFnxGoqtc44lDLrdnt
					Q6/kG7ctT76PpA== )
www.example.com.	3600	IN A	192.0.2.20
			3600	RRSIG	A 13 3 3600 (
					20260614001104 20260515001104 59077 example.com.
					XUhjho363Z+TK+2Hbh47Dd+IYUTF/TgByQJm
					UDzdZOJXepLaHelavY/Ox5632wZqnUwieVwU
					Xxbd8LfxEGHUlg== )
			3600	NSEC	example.com. A RRSIG NSEC
			3600	RRSIG	NSEC 13 3 3600 (
					20260614001104 20260515001104 59077 example.com.
					m6BS5hE5V8lkUnzq6M5zykF6cDvSCBTusGCl
					UbPdJaXoIlyK+GEAKSJHnEmP1VP5kJf5my4U
					YkdVH5l3XVCHhg== )

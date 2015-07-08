#!/usr/bin/env python
import smtplib
import sys

def sendmail(subject, body, fromaddr, toaddrs, smtp_server):
    server = smtplib.SMTP(smtp_server)
    header = ('Subject: %s\r\nFrom: %s\r\nTo: %s\r\n\r\n' % 
        (subject, fromaddr, ', '.join(toaddrs.split())))
    email = ''.join([header, body])
    server.sendmail(fromaddr, toaddrs.split(), email)
    server.quit()

def main(args=sys.argv[1:]):
    subject = args[0]
    body = args[1]
    fromaddr = args[2]
    toaddrs = args[3] 
    smtp_server = args[4]
    sendmail(subject, body, fromaddr, toaddrs, smtp_server)


if __name__ == "__main__":
    main()
    sys.exit()

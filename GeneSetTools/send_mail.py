#!/usr/bin/env python
import smtplib
import sys

def sendmail(subject, body, fromaddr, toaddrs, smtp_server, password):
    #server = smtplib.SMTP(smtp_server)
    server = smtplib.SMTP_SSL('smtp.gmail.com', 465)
    server.ehlo()
    server.login(fromaddr, password)
    header = ('Subject: %s\r\nFrom: %s\r\nTo: %s\r\n\r\n' % 
        (subject, fromaddr, ', '.join(toaddrs.split())))
    email = ''.join([header, body])
    server.sendmail(fromaddr, toaddrs.split(), email)
    server.close()

def main(args=sys.argv[1:]):
    subject = args[0]
    body = args[1]
    fromaddr = args[2]
    toaddrs = args[3] 
    smtp_server = args[4]
    password = args[5]
    sendmail(subject, body, fromaddr, toaddrs, smtp_server,password)


if __name__ == "__main__":
    main()
    sys.exit()

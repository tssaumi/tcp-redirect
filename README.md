# tcp-redirect
If you have application(s) need to access **multiple target(i.e. host + port)** in another network, but firewall only allow you to access a **single port of one machine** in the network, it is a tool for you.  :wink:

It supports two modes:
1. Server & Agents
2. Simple TCP-forwording
### Mode 1 - Server & Agents
Allow different TCP-forwarding through **single TCP listening port**.
Run single or multiple **tcp-redirect agent** (listen mulitple ports) in one network, which connect to a single **tcp-redirect server** (listen on single TCP port) in another network. tcp-redirect agent and tcp-redirect server will have a simple **hand shaking** before they forwarding data from both end.

Sample Case:

An application (192.168.1.34) need to connect following components in another network:
- MySQL (192.168.40.22 port 3306)
- Active Directory (192.168.40.4 port 389)
- Web Server (192.168.40.3 port 80)

192.168.1.* is allowed to access 192.168.40.2 port 8080

Setup:

tcp-redirect agent run on 192.168.1.34
```
mode=agent

redirect.server.host=192.168.40.2
redirect.server.port=8080

channel.MYSQLDB=3306
channel.AD=389
channel.WEB=8080
```

tcp-redirect server run on 192.168.40.2
```
local.port=8080
bind.addr=192.168.40.2

channel.MYSQL=192.168.40.22:3306
channel.AD=192.168.40.4:389
channel.WEB=192.168.40.3:8080
```
### Mode 2 - Simple TCP-forwarding
**tcp-redirect agent** can run as a simple TCP-forwarder.

Sample Case:

Setup TCP-forward for 192.168.1.42:
- port 1521 ==> 192.168.40.4 port 1521
- port 3306 ==> 192.168.40.2 port 3306
```
mode=standalone
channel.MYSQL=3306
channel.ORACLE_DB=1521

```

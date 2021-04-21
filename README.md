# tcp-redirect
If you have application(s) which need to access **multiple target(i.e. host + port)** in another network, but firewall only allow you to access a **single port of one machine** in the network, it is a tool for you.  
tcp-redirect support two mode:
*Server & Agents
*Simple TCP-forwording
## Mode 1 - Server & Agents
Allow different TCP-forwarding route through a single TCP listening port.
Run single or multiple **tcp-redirect agent** (listen mulitple ports) in one network, which connect to a single **tcp-redirect server** (listen on single TCP port) in another network.
## Mode 2 - Simple TCP-forwarding
**tcp-redirect agent** can run as a simple TCP-forwarder.

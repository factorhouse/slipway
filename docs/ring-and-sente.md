On Reddit, when announcing the 2.1.0 release, in response to a very reasonable comment "Bold to reject ring".

https://www.reddit.com/r/Clojure/comments/1vy2hgd/comment/p5vr1cg/?utm_source=share&utm_medium=web3x&utm_name=web3xcss&utm_term=1&utm_content=share_button

----

Author: Derek Troy-West, CEO and Co-Founder, [Factor House](https://factorhouse.io).

We started moving away from Ring about five years ago.

This is a complex issue, because the Clojure community is very enamoured by Ring, and it's my intention to be at first
kind to that community, and particularly the maintainers of the library. Rich Hickey is very clear on his expectations
for collegial behaviour when communicating in this community.

However for numerous reasons it's not possible for my company to use Ring for our work in producing enterprise-grade web
applications. Unfortunately I don't think we have many fellow travellers in this space in the Clojure community today.

We are also moving away from Sente, for the same reasons that we don't use Ring.

I have been working with Jetty for twenty years, I was at one point an fairly active contributor to Netty, I have been
building enterprise-grade web applications professionally since 1999. I happen to love the intricacies of server
frameworks, and the JVM. It's my jam.

I think your opinion is one that is probably fairly well shared within our community, because if you ask a Clojure
developer what web server they use, they will say Ring. That is a problem, because Ring is not a webserver, it is a 
'Clojure HTTP server abstraction' that operates as an application running on top of a webserver, most commonly the
underlying webserver is Jetty.

I don't need a server abstraction, I need to use Jetty. The server abstraction provided by Ring covers a very small part
of the surface area of Jetty. To its credit, Ring supports many other underlying webservers, that is also explicitly not
a goal of Slipway.

I think this is a point to stop and reflect on for a moment. The scope of Eclipse Jetty is enormous, the project is
maintained by an active team of four developers, one of whom has been working on Jetty since before Java existed. Jetty
is used everywhere, including as the underlying servlet framework for Google App Engine. Jetty is a very, very big deal.

Ring sits at the application layer, not the server layer. It is not really possible to have an application provide
server-level functions like request handling, if you follow that path you will end up with users raising
security-related pentest issues that highlight exactly this gap.

https://github.com/factorhouse/kpow/issues/35

Ring is a nice library to use to get started quickly when exploring how Clojure could be used to build a web
application. Sente is also a nice library to use when you first want to get websockets working between the front and
back end. I appreciate the time and effort of the maintainers of those libraries.

Clojure has a larger problem for its future as a viable programming language in which we can produce enterprise-grade
web applications, that is that the state of the art for webservers within Java is Jakarta EE 11 (which include natively
all of the OIDC functionality that I required to implement within Slipway), and that state of the art is moving
increasingly more in a non-Clojurish direction.

I don't particularly want to implement OIDC refresh-token redemption capabilities in Clojure, I need that capability
because if I don't have it I will lose customers. As much as I love Clojure and webservers, I don't want to implement
any Abstract System with the language, I want to use Abstract Systems written in Java in my work, and write my
Information Systems in Clojure. I believe this is one of the key points of Rich
Hickey's [History of Clojure](https://hopl4.sigplan.org/track/hopl-4-papers#History-of-HOPL). This is also the most
profound technical delineation that I have settled on in my understanding of programming, and I'm not sure it's a well
understood point by programmers of any language, in general.

I would love to use Jakarta Security 4.0 which is new in Jakarta EE 11 and provides full native support for OIDC
already. Jetty is already well across this space with optional modular support for EE 8, 9, 10, and 11. So why did I
choose to use the non-servlet, non-EE, native handlers of Jetty in Slipway?

It's because in Jakarte EE 11 Context and Dependency Injection (CDI) officially becomes the single, unified component
model across the entire enterprise Java ecosystem, completely replacing legacy technologies. You can think of this as EE
slowly eating Spring DI.

CDI is entirely driven by annotations, and annontations are entirely not Clojurish. That is positive by the way,
configuring systems in code is a pale imitation of configuring systems in data. I much prefer my data-oriented system
config library in Clojure to annotations in Java.

However it presents a big problem for Clojure, because there is a fundamental language-level construct that is driving
Java away from Clojure in this space in the state of the art.

This annotation issue is also why there is no supporting library in Clojure for much more expansive web-frameworks like
Quarkus. Quarkus also uses annotations for everthing, and to use Clojure and Quarkus you would most likely have to
simply have Clojure fire up within the handler and only run at the application layer, which is where Ring runs today.

I want Clojure to control the abstract framework, not the other way around. So here I am implementing OIDC in Jetty, so
I can use it in Clojure, to keep my customers happy, by beating the ever-loving beguck out of my competitors, by using
Clojure to build my informational system, which is the application that runs on top of Slipway.

I love Clojure, but if it was not an economic advantage to me I would stop using it. Currently it is worth the small
commitment of pushing my own networking library forward, but if you look at the "Future Work" section fo the readme,
there is further work required and it is not small.

I should clarify that 'small work' in this case is 800 commits over five years and the continual effort required
throughout that five year period to remain current enough to write this post.

I hope this post has been informational to you, and has been polite in its reference to the hard and valuable work
provided by the maintainers of the libraries that I am moving away from.

It was necessary to be clear in the readme of Slipway as to the fact that Slipway intends to remove Ring and Sente
entirely, because that is a technical necessity for me.

I will add this comment in full to the docs folder of Slipway and link it from the README.

As I said, I'm a datomic fanboy, so I couldn't resist, and (not to blow my own trumpet) the effort required [no changes to existing code](https://github.com/p14n/events-unbundled/commit/30c51af3b513d8f4944f80b64d60b6103af06bea).

Pros
* We keep the massive read-scale of XTDB
* We almost keep the historical view of data
* graphqlqueries -> datalog queries can be codified, so you don't need a projector anymore (I didn't need one here anyway because `(pull [*])` worked, but you won't need to write bespoke ones anyway)
* We keep the refential interity of postgres
* The data the `operator` writes can be used in queries by a responder/other services without needing to operate on the same shape of data (ie datomic is a graph db, not a document db) 

Cons
* We lose the true view of history (it's based on system time, not business time, which is important if data has been amended)
* We will need to write transation functions for some of our data interity requirements - its not as simple as SQL
* Datomic needs special attention [beyond 10^9 datoms](https://ask.datomic.com/index.php/403/what-is-the-size-limit-of-a-datomic-cloud-database#:~:text=There%20is%20no%20hard%20limit,more%20than%2010%20billion%20datoms.) (a datom is a fact like `{:customer:email "dean@p14n.com"}`)
* Datomic is also single-writer, however db-per-service can be used to mitigate this (and graphql will make it simple to select from multiple datomic sources in a single http request)

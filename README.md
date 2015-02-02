Leo-III
=======
*A massively parallel higher-order theorem prover*

In the Leo-III project, we
design and implement a state-of-the-art Higher-Order
Logic Theorem Prover, the successor of the well known
LEO-II prover [[2](http://dx.doi.org/10.1007/978-3-540-71070-7_14)]. Leo-III will be based on ordered
paramodulation/superposition.
In constrast to LEO-II, we replace the internal term representation
(the commonly used simply typed lambda-calculus)
by a more expressive system supporting type polymorphism.
In order to achieve a substantial performance speed-up,
the architecture of Leo-III will be based on massive parallelism
(e.g. And/Or-Parallelism, Multisearch) [[3](http://dx.doi.org/10.1023/A:1018932114059)]. The
current design is a multi-agent blackboard architecture
that will allow to independently run agents with our proof
calculus as well as agents for external (specialized) provers.
Leo-III will focus right from the start on compatibility to
the widely used TPTP infrastructure [[8](http://dx.doi.org/10.1007/s10817-009-9143-8)]. Moreover, it
will offer built-in support for specialized external prover
agents and provide external interfaces to interactive provers
such as Isabelle/HOL [[5](http://dx.doi.org/10.1007/3-540-45949-9)]. The implementation will excessively
use term sharing [[6](http://dl.acm.org/citation.cfm?id=1218621), [7](http://dl.acm.org/citation.cfm?id=1218620)] and several indexing techniques
[[4](dx.doi.org/10.1007/3-540-45744-5_19), [9](dx.doi.org/10.1007/978-3-540-71070-7_14)]. Leo-III will also offer special support for
reasoning in various quantified non-classical logics by exploiting
a semantic embedding [[1](dx.doi.org/10.5220/0004324803460351)] approach.

Further information can be found at the [Leo-III Website](http://page.mi.fu-berlin.de/lex/leo3/).

Building the project
----------------

[Maven](http://maven.apache.org/) manages the build process of Leo-III. Information about downloading and installing Maven can be found at [the download section of the maven website](http://maven.apache.org/download.cgi).

The project is compiled and built into an executable `.jar` file usng

    > mvn compile
    > mvn assembly:single


For an easier access the makefile can be used. Invoking

    > make

will result in the same `.jar`
    
All test suits are ran by
    
    > mvn test
    
The compiled test class files will be placed at `./target/test-classes/`.

The sole compilation process can be started by typing

    > mvn compile

The compiled files (class files) will be placed at `./target/classes/`.


Project's current structure
--------------

This section is a stub. It will be expanded in the future.

```
└── leo                 -- Where the Main executable is located
    ├── agents          -- Specification of agents
    │   └── impl        -- Implementation of agents
    ├── datastructures  -- root packagef or various data structures
    │   ├── blackboard
    │   │   ├── impl
    │   │   └── scheduler
    │   ├── context
    │   │   └── impl
    │   ├── impl        -- Here are most of the implementations located
    │   ├── term
    │   │   ├── naive
    │   │   └── spine
    │   └── tptp        -- Internal syntax representation of TPTP
    │       ├── cnf
    │       ├── fof
    │       ├── tff
    │       └── thf
    └── modules          -- All sorts of functionality modules
        ├── churchNumerals
        ├── normalization
        ├── output
        │   └── logger
        ├── parsers
        │   ├── lexical
        │   └── syntactical
        ├── proofCalculi
        │   └── resolution
        └── visualization
```

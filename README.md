# 💬 HeapTalk

HeapTalk is a proof-of-concept utility created to check if it is possible to “chat” with JVM heap dumps by means of [LLMs](https://en.wikipedia.org/wiki/Large_language_model). Seems like it is.

The utility lets you ask questions about heap dump data in human language by translating them into SQL queries for execution with [MAT Calcite SQL Plugin](https://github.com/vlsi/mat-calcite-plugin).

### Usage example

First, download the executable distribution from [Releases](releases/latest) page. Then make sure you have **Java 21** installed.

```sh
./bin/heap-talk --api-key=demo dumps/petclinic.hprof \
  org.springframework.samples.petclinic.model \
  "What's the phone number of the person whose hamster named Basil?"
```

By default, the output contains only the generated SQL:

```sql
SELECT o.telephone 
FROM "org.springframework.samples.petclinic.model.Owner" AS o 
JOIN "org.springframework.samples.petclinic.model.Pet" AS p ON o.this = p.owner 
WHERE toString(p.name) = 'Basil'
```

, but you may like to see more details (including generated DDL statements) by adding `--verbose` option.

See more examples on [this page](https://toparvion.pro/project/heap-talk/) (in Russian only so far).

### Feedback

You’re welcome to leave [comments](https://toparvion.pro/en/#contact), submit [issues](issues/) and create [PRs](pulls/) if you find this tools interesting.


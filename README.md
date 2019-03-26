# Flerken

## Motivation
To give the means to companies or individuals who cannot maintain a Kafka cluster (or derivatives) achieve similar results.

These are the goals:

1. Use http with short running connections.
2. Optimistic commits via a persisted storage (go back in time by requesting tasks to be re-executed).
3. Configurable retention period for both failed and successful tasks.
4. Load balancing for the workers - achieve similar results to partitioning 

This could be used as a playground for prototyping.

Obviously reliability and performance will be worse than using Kafka. But if you have the traffic that exceeds the limitation of this, you should consider building a Kafka cluster. 

In future iterations maybe I'll put support for Kafka and Kinesis, but there are so many paradigms around these technologies that for now it's out of scope.

## Architecture (vision)

![architecture](https://docs.google.com/drawings/d/e/2PACX-1vQRZQ34GfLlTh2eVLItmcPgGlI-9_GqM4rZnZQz-MpCo0824kgOKieXjB9y_qQIaEUdXwgJwEtyNRfd/pub?w=960&h=720)

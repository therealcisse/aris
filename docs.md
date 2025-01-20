### CQRS Core Module Documentation

**Introduction**

The `cqrs-core` module provides a set of interfaces that form the foundation for building robust and scalable systems for managing state and lifecycle. This documentation outlines the core components, their responsibilities, and how they interact to provide a comprehensive framework for building CQRS-based applications.

**The Job Module: A Practical Implementation**

The `job` module serves as a concrete example of how to leverage the `cqrs-core` framework. It defines the following components:

* **JobCQRS:** An interface extending the `CQRS` trait from `cqrs-core`. It defines methods for handling `JobCommand`s and generating corresponding `JobEvent`s.

```scala
trait JobCQRS extends CQRS[JobEvent, JobCommand] {}
```

* **JobEventStore:** An interface responsible for persisting and retrieving `JobEvent`s. It leverages the `EventStore` trait from `cqrs-core`.

```scala
trait JobEventStore extends EventStore[JobEvent] {
  // ...
}
```

* **JobCommand and Command Handlers:** `JobCommand` is an enumeration representing various commands related to job management (e.g., `StartJob`, `ReportProgress`, `CancelJob`). Command handlers implement the `CmdHandler` trait and define how each `JobCommand` translates into a sequence of `JobEvent`s.

```scala
enum JobCommand {
  case StartJob(id: Job.Id, total: JobMeasurement, tag: Job.Tag)
  case ReportProgress(id: Job.Id, progress: Progress)
  case CancelJob(id: Job.Id)
  case CompleteJob(id: Job.Id, reason: Job.CompletionReason)
}
```

* **JobEvent and Event Handlers:** `JobEvent` is an enumeration representing events related to the job lifecycle (e.g., `JobStarted`, `ProgressReported`, `JobCompleted`). Event handlers implement the `EventHandler` trait and define how to reconstruct the current state of a job (`Job`) from a sequence of `JobEvent`s.

```scala
enum JobEvent {
  case JobStarted(id: Job.Id, total: JobMeasurement, tag: Job.Tag)
  case ProgressReported(id: Job.Id, progress: Progress)
  case JobCompleted(id: Job.Id, reason: Job.CompletionReason)
}
```

* **JobService:** A high-level interface that provides methods for interacting with jobs, such as starting a job, reporting progress, completing a job, and retrieving job details. It encapsulates the underlying CQRS logic.

```scala
trait JobService {
  def isCancelled(id: Job.Id): Task[Boolean]

  def load(id: Job.Id): Task[Option[Job]]
  def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Job]]
  def save(job: Job): Task[Long]

  def cancelJob(id: Job.Id): Task[Unit]
  def startJob(id: Job.Id, total: JobMeasurement, tag: Job.Tag): Task[Unit]
  def reportProgress(id: Job.Id, progress: Progress): Task[Unit]
  def completeJob(id: Job.Id, reason: Job.CompletionReason): Task[Unit]
}
```
* **Job:** A case class representing the state of a job. It includes the job ID, tag, total measurement, and status.

```scala
case class Job(
  id: Job.Id,
  tag: Job.Tag,
  total: JobMeasurement,
  status: JobStatus,
)
```
**Snapshots and Strategy**

The `cqrs-core` module provides support for snapshots to optimize the loading of aggregates. Snapshots are point-in-time representations of an aggregate's state. The `SnapshotStore` interface defines methods for reading and saving snapshots.

```scala
trait SnapshotStore {
  def readSnapshot(id: Key): RIO[ZConnection, Option[Version]]
  def save(id: Key, version: Version): RIO[ZConnection, Long]
}
```

The `SnapshotStrategy` trait defines when to take a snapshot. The `SnapshotStrategy.Factory` creates a `SnapshotStrategy` for a given discriminator.

```scala
trait SnapshotStrategy {
  def apply(version: Option[Version], n: Int): Boolean
}

object SnapshotStrategy {
  trait Factory {
    def create(discriminator: Discriminator): Task[SnapshotStrategy]
  }
}
```
**Schema Evolution**

The system is designed to be flexible with the addition of new commands and events. The `CmdHandler` and `EventHandler` interfaces allow for the evolution of the system without affecting existing functionality.

**Version**

The `Version` type serves as a unique identifier and timestamp for events. This simplifies the management of events and ensures that they are ordered by time.

**Testing**

The modular design of the system makes it easy to test individual components. The `MockCQRSPersistence` and `MockSnapshotStore` classes provide mocks for the persistence layer, allowing for unit testing of the core logic.

**Further Enhancements**

The documentation can be further improved by:

* Adding more detailed explanations of each component.
* Providing more examples of how to use the framework.
* Creating tutorials for common use cases.
* Adding a troubleshooting section.

**Conclusion**

The `cqrs-core` module provides a solid foundation for building scalable and robust CQRS-based applications. Its modular design and well-defined interfaces make it easy to use, test, and extend. By following the principles outlined in this documentation, you can build systems that are maintainable, scalable, and adaptable to future changes.

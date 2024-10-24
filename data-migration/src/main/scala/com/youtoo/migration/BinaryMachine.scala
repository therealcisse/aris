// package com.youtoo
// package migration
//
// import zio.*
//
// trait BinaryMachine {
//   def watch(id: Key): Task[Promise[Any, Unit]]
// }
//
// object BinaryMachine {
//   inline def watch(id: Key): RIO[BinaryMachine, Promise[Any, Unit]] =
//     ZIO.serviceWithZIO(_.watch(id))
//
//   def live(): ZLayer[Any, Throwable, BinaryMachine] =
//     ZLayer {
//       Ref.Synchronized.make(Map.empty[Key, Promise[Any, Unit]]) map { ref =>
//         new BinaryMachine {
//
//           def watch(id: Key): Task[Promise[Any, Unit]] =
//             ref.modifyZIO { s =>
//               s.get(id) match {
//                 case None =>
//                   Promise.make[Any, Unit] map { p =>
//                     (p, s + (id -> p))
//                   }
//
//                 case Some(p) =>
//                   ZIO.succeed((p, s))
//               }
//
//             }
//
//         }
//
//       }
//
//     }
// }

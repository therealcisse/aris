package com.github
package aris
package tenants

import aris.*

final case class NameTenant(
  id: TenantId,
  namespace: Namespace,
  name: String,
  description: String,
  created: Timestamp,
  status: NameTenant.Status,
)

object NameTenant {
  enum Status {
    case Active, Disabled, Deleted
  }

  val root: NameTenant =
    NameTenant(
      TenantId.wrap(0),
      Namespace.root,
      "Root Namespace",
      "The description",
      Timestamp.wrap(0L),
      Status.Active,
    )
}

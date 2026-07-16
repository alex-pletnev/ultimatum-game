package edu.itmo.ultimatumgame.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "npc_profile")
class NpcProfile(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @field:OneToOne(optional = false, fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id", unique = true, nullable = false)
    var user: User,

    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var strategy: NpcStrategy,

    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "params_json", nullable = false, columnDefinition = "jsonb")
    var params: NpcParams,

    @field:Column
    var seed: Long? = null,

    @field:Column(nullable = false, updatable = false)
    var createdAt: Date = Date(),
)

@file:Suppress("MaxLineLength", "MaximumLineLength")

package edu.itmo.ultimatumgame.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.proxy.HibernateProxy
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "offer")
class Offer(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "session_id", nullable = false)
    var session: Session? = null,

    @field:ManyToOne(optional = false)
    @field:JoinColumn(name = "round_id", nullable = false)
    var round: Round? = null,

    @field:ManyToOne(optional = false)
    @field:JoinColumn(name = "proposer_id", nullable = false)
    var proposer: User? = null,
    @field:ManyToOne
    @field:JoinColumn(name = "responder_id", nullable = true)
    var responder: User? = null,

    @field:Column(nullable = false)
    var offerValue: Int,

    @field:Column(nullable = false, updatable = false)
    var createdAt: Date = Date(),

) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        val oEffectiveClass =
            if (other is HibernateProxy) other.hibernateLazyInitializer.persistentClass else other.javaClass
        val thisEffectiveClass =
            if (this is HibernateProxy) this.hibernateLazyInitializer.persistentClass else this.javaClass
        if (thisEffectiveClass != oEffectiveClass) return false
        other as Offer

        return id != null && id == other.id
    }

    final override fun hashCode(): Int =
        if (this is HibernateProxy) this.hibernateLazyInitializer.persistentClass.hashCode() else javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(  id = $id   ,   round = $round   ,   proposer = $proposer   ,   responder = $responder   ,   offerValue = $offerValue )"
    }
}

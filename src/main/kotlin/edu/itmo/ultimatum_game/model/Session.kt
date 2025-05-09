package edu.itmo.ultimatum_game.model

import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy
import java.util.*

@Entity
@Table(name = "session")
class Session(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @field:Column(nullable = false)
    var displayName: String = "",
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var state: SessionState = SessionState.CREATED,
    @field:Column(nullable = false)
    var createdAt: Date = Date(),
    @field:ManyToOne(optional = false)
    var admin: User? = null,
    var openToConnect: Boolean = true,
    @OneToOne
    @JoinColumn(name = "current_round_id")
    var currentRound: Round? = null,


    @OneToMany(
        mappedBy = "session",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        orphanRemoval = true
    )
    var rounds: MutableList<Round> = mutableListOf(),

    @field:Embedded
    var config: SessionConfig? = null,

    @field:OneToMany(
        mappedBy = "session",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        orphanRemoval = true
    )
    var teams: MutableSet<Team> = mutableSetOf(),

    @field:ManyToMany(cascade = [CascadeType.MERGE])
    var members: MutableSet<User> = mutableSetOf(),

    @field:ManyToMany(cascade = [CascadeType.MERGE])
    var observers: MutableSet<User> = mutableSetOf(),
    ) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        val oEffectiveClass =
            if (other is HibernateProxy) other.hibernateLazyInitializer.persistentClass else other.javaClass
        val thisEffectiveClass =
            if (this is HibernateProxy) this.hibernateLazyInitializer.persistentClass else this.javaClass
        if (thisEffectiveClass != oEffectiveClass) return false
        other as Session

        return id != null && id == other.id
    }

    final override fun hashCode(): Int =
        if (this is HibernateProxy) this.hibernateLazyInitializer.persistentClass.hashCode() else javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(  id = $id   ,   displayName = $displayName   ,   state = $state   ,   createdAt = $createdAt   ,   admin = $admin   ,   config = $config )"
    }
}

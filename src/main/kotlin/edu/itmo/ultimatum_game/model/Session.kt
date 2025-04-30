package edu.itmo.ultimatum_game.model

import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy
import java.util.*

@Entity
@Table(name = "session")
data class Session(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @field:Column(nullable = false)
    val displayName: String,
    @field:Enumerated(EnumType.STRING)
    @field:Column(nullable = false)
    var state: SessionState = SessionState.CREATED,
    @field:Column(nullable = false)
    val createdAt: Date = Date(),
    @field:ManyToOne(optional = false)
    var admin: User? = null,

    @field:Embedded
    val config: SessionConfig,
    @field:OneToMany(
        mappedBy = "session",
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE],
        orphanRemoval = true
    )

    var teams: MutableList<Team> = mutableListOf(),

    @field:ManyToMany(cascade = [CascadeType.MERGE])
    val members: MutableList<User>,

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

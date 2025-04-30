package edu.itmo.ultimatum_game.model

import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy
import java.util.*

@Entity
data class Team(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @field:Column(nullable = false)
    val name: String,
    @ManyToMany(cascade = [CascadeType.MERGE])
    val members: MutableList<User>,

    ) {
    @ManyToOne
    @JoinColumn(name = "session_id")
    var session: Session? = null

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        val oEffectiveClass =
            if (other is HibernateProxy) other.hibernateLazyInitializer.persistentClass else other.javaClass
        val thisEffectiveClass =
            if (this is HibernateProxy) this.hibernateLazyInitializer.persistentClass else this.javaClass
        if (thisEffectiveClass != oEffectiveClass) return false
        other as Team

        return id != null && id == other.id
    }

    final override fun hashCode(): Int =
        if (this is HibernateProxy) this.hibernateLazyInitializer.persistentClass.hashCode() else javaClass.hashCode()

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(  id = $id   ,   name = $name   ,   session = $session )"
    }


}

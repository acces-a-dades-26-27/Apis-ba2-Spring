package cat.iesabadell.videojocs.model;

import jakarta.persistence.*;

/*
 * Catàleg de plataformes (PC, PS5, Switch).
 * No té relacions pròpies: és Llancament qui apunta a Plataforma (unidireccional).
 * Per això es pot retornar directament com a JSON sense cap problema.
 */
@Entity
@Table(name="plataforma")
public class Plataforma {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;

	@Column(name="nom", nullable=false, unique=true, length=30)
	private String nom;

	// JPA necessita el constructor buit
	public Plataforma() {
	}

	public Plataforma(String nom) {
		this.nom = nom;
	}

	public Integer getId() {
		return id;
	}

	public String getNom() {
		return nom;
	}

	public void setNom(String nom) {
		this.nom = nom;
	}

	// equals/hashCode: patró de la sessió 1 d'Hibernate (id Integer, que és null fins que es desa)
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Plataforma other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Plataforma.class.hashCode();
	}

	@Override
	public String toString() {
		return "Plataforma [id=" + id + ", nom=" + nom + "]";
	}

}

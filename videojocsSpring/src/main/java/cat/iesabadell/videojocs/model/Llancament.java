package cat.iesabadell.videojocs.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

/*
 * N:M AMB ATRIBUTS entre Videojoc i Plataforma: el preu depèn de la parella.
 * Sense clau composta: id propi + UNIQUE(videojoc_id, plataforma_id).
 */
@Entity
@Table(name="llancament",
		uniqueConstraints=@UniqueConstraint(columnNames={"videojoc_id", "plataforma_id"}))
public class Llancament {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;

	// LAZY: referència de tornada. @JsonIgnore: tallem el bucle videojoc -> llancaments -> videojoc
	@ManyToOne(fetch=FetchType.LAZY, optional=false)
	@JoinColumn(name="videojoc_id", nullable=false)
	@JsonIgnore
	private Videojoc videojoc;

	// EAGER: un preu sense plataforma no diu res, i el catàleg és petit
	@ManyToOne(fetch=FetchType.EAGER, optional=false)
	@JoinColumn(name="plataforma_id", nullable=false)
	private Plataforma plataforma;

	@Column(name="preu", nullable=false, precision=5, scale=2)
	private BigDecimal preu;

	public Llancament() {
	}

	public Llancament(Videojoc videojoc, Plataforma plataforma, BigDecimal preu) {
		this.videojoc = videojoc;
		this.plataforma = plataforma;
		this.preu = preu;
	}

	public Integer getId() {
		return id;
	}

	public Videojoc getVideojoc() {
		return videojoc;
	}

	public Plataforma getPlataforma() {
		return plataforma;
	}

	public BigDecimal getPreu() {
		return preu;
	}

	public void setPreu(BigDecimal preu) {
		this.preu = preu;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Llancament other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Llancament.class.hashCode();
	}

	@Override
	public String toString() {
		return "Llancament [id=" + id + ", preu=" + preu + "]";
	}

}

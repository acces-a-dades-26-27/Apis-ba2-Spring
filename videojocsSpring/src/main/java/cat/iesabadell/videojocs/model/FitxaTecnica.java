package cat.iesabadell.videojocs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name="fitxa_tecnica")
public class FitxaTecnica {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;

	@Column(name="motor", nullable=false, length=50)
	private String motor;

	/*
	 * 1:1 costat PROPIETARI: aquí hi ha la FK videojoc_id (UNIQUE).
	 * LAZY: és la referència de tornada; sempre arribem a la fitxa des del videojoc.
	 * @JsonIgnore: tallem el bucle Videojoc -> fitxa -> videojoc -> ...
	 */
	@OneToOne(fetch=FetchType.LAZY, optional=false)
	@JoinColumn(name="videojoc_id", nullable=false, unique=true)
	@JsonIgnore
	private Videojoc videojoc;

	public FitxaTecnica() {
	}

	public FitxaTecnica(String motor) {
		this.motor = motor;
	}

	public Integer getId() {
		return id;
	}

	public String getMotor() {
		return motor;
	}

	public void setMotor(String motor) {
		this.motor = motor;
	}

	public Videojoc getVideojoc() {
		return videojoc;
	}

	public void setVideojoc(Videojoc videojoc) {
		this.videojoc = videojoc;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof FitxaTecnica other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return FitxaTecnica.class.hashCode();
	}

	@Override
	public String toString() {
		return "FitxaTecnica [id=" + id + ", motor=" + motor + "]";
	}

}

package cat.iesabadell.videojocs.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name="estudi")
public class Estudi {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;

	@Column(name="nom", nullable=false, unique=true, length=50)
	private String nom;

	/*
	 * 1:N costat INVERS (la FK estudi_id és a la taula videojoc).
	 * cascade + orphanRemoval: esborrar un estudi esborra TOTS els seus videojocs
	 *   -> per això els estudis no s'esborren des de l'API: tot es gestiona des de Videojoc.
	 * LAZY: és una col·lecció que creix sense límit.
	 * @JsonIgnore: no surt al JSON. Sense això, en retornar un Estudi:
	 *   - amb open-in-view=false -> LazyInitializationException (la llista no està carregada)
	 *   - amb open-in-view=true  -> bucle infinit Estudi -> videojocs -> estudi -> videojocs...
	 * L'entitat sempre ha de ser List (interfície), no ArrayList: Hibernate hi posa la seva pròpia llista.
	 */
	@OneToMany(mappedBy="estudi", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.LAZY)
	@JsonIgnore
	private List<Videojoc> videojocs = new ArrayList<>();

	public Estudi() {
	}

	public Estudi(String nom) {
		this.nom = nom;
	}

	// Mètode auxiliar: sincronitza els dos costats de la relació
	public void afegirVideojoc(Videojoc v) {
		videojocs.add(v);
		v.setEstudi(this);
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

	public List<Videojoc> getVideojocs() {
		return videojocs;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Estudi other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Estudi.class.hashCode();
	}

	@Override
	public String toString() {
		return "Estudi [id=" + id + ", nom=" + nom + "]";
	}

}

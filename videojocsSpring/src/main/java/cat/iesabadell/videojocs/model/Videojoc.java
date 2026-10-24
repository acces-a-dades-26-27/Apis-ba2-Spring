package cat.iesabadell.videojocs.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

/*
 * L'entitat central del model. NO la retornem mai directament com a JSON:
 * té relacions LAZY (llancaments) que, fora del servei, peten. Per a l'API fem servir
 * els DTO VideojocResum i VideojocResposta, que es construeixen DINS del servei.
 */
@Entity
@Table(name="videojoc")
public class Videojoc {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;

	@Column(name="nom", nullable=false, unique=true, length=100)
	private String nom;

	/*
	 * N:1 costat PROPIETARI (FK estudi_id).
	 * EAGER: una sola referència i es mostra gairebé sempre amb el joc.
	 */
	@ManyToOne(fetch=FetchType.EAGER, optional=false)
	@JoinColumn(name="estudi_id", nullable=false)
	private Estudi estudi;

	/*
	 * 1:1 costat INVERS (la FK és a fitxa_tecnica).
	 * Al costat invers d'una 1:1 el LAZY no funciona: es comporta com EAGER.
	 */
	@OneToOne(mappedBy="videojoc", cascade=CascadeType.ALL, orphanRemoval=true)
	private FitxaTecnica fitxa;

	/*
	 * N:M PURA: taula intermèdia videojoc_categoria que gestiona Hibernate.
	 * Set (no List): en treure'n una, Hibernate només esborra aquella fila.
	 * EAGER: poques per joc i es mostren amb el joc. Sense cascade: una categoria no és del videojoc.
	 */
	@ManyToMany(fetch=FetchType.EAGER)
	@JoinTable(name="videojoc_categoria",
			joinColumns=@JoinColumn(name="videojoc_id"),
			inverseJoinColumns=@JoinColumn(name="categoria_id"))
	private Set<Categoria> categories = new HashSet<>();

	/*
	 * N:M AMB ATRIBUTS: entitat intermèdia Llancament (és el que farem a Catan).
	 * LAZY: només cal quan parlem de preus.
	 */
	@OneToMany(mappedBy="videojoc", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.LAZY)
	private List<Llancament> llancaments = new ArrayList<>();

	public Videojoc() {
	}

	public Videojoc(String nom) {
		this.nom = nom;
	}

	// ---------- mètodes auxiliars (sincronitzen els dos costats) ----------

	public void setFitxa(FitxaTecnica fitxa) {
		this.fitxa = fitxa;
		if (fitxa != null) {
			fitxa.setVideojoc(this);
		}
	}

	public void afegirCategoria(Categoria c) {
		categories.add(c);
	}

	public void treureCategoria(Categoria c) {
		categories.remove(c);
	}

	public void afegirLlancament(Plataforma p, BigDecimal preu) {
		llancaments.add(new Llancament(this, p, preu));
	}

	// ---------- getters / setters ----------

	public Integer getId() {
		return id;
	}

	public String getNom() {
		return nom;
	}

	public void setNom(String nom) {
		this.nom = nom;
	}

	public Estudi getEstudi() {
		return estudi;
	}

	public void setEstudi(Estudi estudi) {
		this.estudi = estudi;
	}

	public FitxaTecnica getFitxa() {
		return fitxa;
	}

	public Set<Categoria> getCategories() {
		return categories;
	}

	public List<Llancament> getLlancaments() {
		return llancaments;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Videojoc other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Videojoc.class.hashCode();
	}

	@Override
	public String toString() {
		return "Videojoc [id=" + id + ", nom=" + nom + "]";
	}

}

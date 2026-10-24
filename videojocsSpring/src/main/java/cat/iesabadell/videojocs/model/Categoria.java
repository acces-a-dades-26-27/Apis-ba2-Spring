package cat.iesabadell.videojocs.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

/*
 * Relació RECURSIVA: una categoria té una categoria pare (o cap, si és arrel) i moltes filles.
 *   Acció -> Shooter, Plataformes, Roguelike
 *   RPG   -> JRPG
 */
@Entity
@Table(name="categoria")
public class Categoria {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;

	@Column(name="nom", nullable=false, unique=true, length=30)
	private String nom;

	/*
	 * Cap AMUNT (N:1, propietari, FK pare_id). nullable: les arrels no tenen pare.
	 * EAGER: la cadena d'ancestres és curta (la profunditat de l'arbre).
	 * Surt al JSON: una categoria es mostra amb el seu pare, el pare del pare...
	 */
	@ManyToOne(fetch=FetchType.EAGER)
	@JoinColumn(name="pare_id")
	private Categoria pare;

	/*
	 * Cap AVALL (1:N, invers). LAZY: EAGER carregaria tot el subarbre.
	 * @JsonIgnore: si sortissin les filles i també el pare, bucle infinit pare -> filles -> pare...
	 * cascade: desar una arrel desa tot el seu subarbre (es fa servir a la càrrega dels CSV).
	 */
	@OneToMany(mappedBy="pare", cascade=CascadeType.ALL, orphanRemoval=true)
	@OrderBy("nom")
	@JsonIgnore
	private List<Categoria> filles = new ArrayList<>();

	public Categoria() {
	}

	public Categoria(String nom) {
		this.nom = nom;
	}

	// Mètode auxiliar: sincronitza els dos costats
	public void afegirFilla(Categoria filla) {
		filles.add(filla);
		filla.setPare(this);
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

	public Categoria getPare() {
		return pare;
	}

	public void setPare(Categoria pare) {
		this.pare = pare;
	}

	public List<Categoria> getFilles() {
		return filles;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Categoria other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Categoria.class.hashCode();
	}

	@Override
	public String toString() {
		return "Categoria [id=" + id + ", nom=" + nom + "]";
	}

}

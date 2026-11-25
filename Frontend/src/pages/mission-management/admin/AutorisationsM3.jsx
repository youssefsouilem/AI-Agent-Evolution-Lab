const rows = [
  ['Consulter ses missions', 'Utilisateur', 'Oui'],
  ['Créer, modifier, exécuter', 'Utilisateur', 'Oui'],
  ['Inspecter toutes les missions', 'Administrateur', 'Oui'],
  ['Paramètres globaux M3', 'Administrateur', 'Oui']
];
export default function AutorisationsM3() {
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">M3 / Administration</div>
          <h1>Autorisations M3</h1>
          <p>
            Présentation illustrative des permissions. Le sélecteur de rôle
            n'est pas un contrôle de sécurité.
          </p>
        </div>
      </div>
      <section className="m3-panel">
        <h2>Matrice de démonstration</h2>
        <table className="m3-table">
          <thead>
            <tr>
              <th>Permission</th>
              <th>Rôle</th>
              <th>Démo</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([a, b, c]) => (
              <tr key={a}>
                <td>{a}</td>
                <td>{b}</td>
                <td>{c}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="muted">
          L'authentification et l'autorisation réelles devront être réalisées
          ultérieurement via l'infrastructure commune, par exemple Keycloak.
        </p>
      </section>
    </div>
  );
}

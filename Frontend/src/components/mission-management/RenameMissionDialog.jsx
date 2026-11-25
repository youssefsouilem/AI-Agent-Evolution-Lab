import { useEffect, useState } from 'react';
import Modal from '../common/Modal';

export default function RenameMissionDialog({ mission, onClose, onSave }) {
  const [name, setName] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    setName(mission?.name || '');
    setError('');
  }, [mission]);
  return (
    <Modal open={!!mission} title="Renommer la mission" onClose={onClose}>
      {error && (
        <p role="alert" className="m3-error">
          {error}
        </p>
      )}
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          if (!name.trim() || pending) return;
          setPending(true);
          setError('');
          try {
            await onSave(name.trim());
          } catch (err) {
            setError(err.message);
          } finally {
            setPending(false);
          }
        }}
      >
        <label className="m3-form-label" htmlFor="m3-rename">
          Nouveau nom
        </label>
        <input
          id="m3-rename"
          autoFocus
          className="field"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={80}
        />
        {!name.trim() && (
          <small className="m3-error">Le nom ne peut pas être vide.</small>
        )}
        <div className="m3-modal-actions">
          <button type="button" className="btn btn-outline" onClick={onClose}>
            Annuler
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={!name.trim() || pending}
          >
            Enregistrer
          </button>
        </div>
      </form>
    </Modal>
  );
}

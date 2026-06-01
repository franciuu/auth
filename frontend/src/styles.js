// Minimal inline styles shared across pages (no CSS files).
export const styles = {
  container: {
    maxWidth: '400px',
    margin: '40px auto',
    padding: '20px',
    fontFamily: 'system-ui, sans-serif',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '10px',
  },
  input: {
    padding: '10px',
    fontSize: '14px',
  },
  button: {
    padding: '10px 20px',
    cursor: 'pointer',
  },
  error: { color: 'red' },
  success: { color: 'green' },
  hint: { color: '#666', fontSize: '12px' },
  table: {
    borderCollapse: 'collapse',
    width: '100%',
    marginTop: '10px',
  },
  cell: {
    border: '1px solid #ddd',
    padding: '6px 8px',
    fontSize: '13px',
    textAlign: 'left',
  },
};

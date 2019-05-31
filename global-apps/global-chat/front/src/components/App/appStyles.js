const appStyles = theme => {
  return {
    root: {
      display: 'flex',
      flexDirection: 'row',
      backgroundImage: 'linear-gradient(120deg, #e0c3fc 0%, #8ec5fc 100%)',
      height: '100vh'
    },
    chatWrapper: {
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      height: '100vh',
      margin: '0 auto',
      flexGrow: 1
    },
    grow: {
      flexGrow: 1
    },
    headerPadding: theme.mixins.toolbar
  }
};

export default appStyles;

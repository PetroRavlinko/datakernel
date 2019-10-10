import { createMuiTheme } from '@material-ui/core/styles';

const theme = createMuiTheme({
  palette: {
    primary: {
      main: '#3e79ff',
      background: '#f8f8f8',
      contrastText: '#fff',
      darkWhite: '#f5f5dc'
    },
    secondary: {
      main: '#f44336',
      contrastText: '#000',
      grey: '#66666680',
      lightGrey: 'rgba(0, 0, 0, 0.05)',
      darkGrey: '#808080'
    }
  }
});

export default theme;

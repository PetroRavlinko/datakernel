import React from 'react';
import connectService from '../../common/connectService';
import ChatContext from '../../modules/chat/ChatContext';
import Paper from '@material-ui/core/Paper';
import SendIcon from '@material-ui/icons/Send';
import messageFormStyles from './MessageFormStyles';
import {withStyles} from '@material-ui/core';
import InputBase from '@material-ui/core/InputBase';
import Divider from '@material-ui/core/Divider';
import IconButton from '@material-ui/core/IconButton';

class MessageForm extends React.Component {
  state = {
    login: '',
    message: ''
  };

  onChangeField = (event) => {
    this.setState({
      [event.target.name]: event.target.value
    });
  };

  onSubmit = (event) => {
    event.preventDefault();
    if (!this.state.message) {
      return;
    }
    this.props.sendMessage(this.props.login, this.state.message);
    this.setState({
      message: ''
    });
  };

  render() {

    return (
      <form onSubmit={this.onSubmit}>
        <Paper className={this.props.classes.root} elevation={2}>
          <InputBase
            inputProps={{
              className: this.props.classes.inputText
            }}
            className={this.props.classes.input}
            placeholder="Message"
            name="message"
            onChange={this.onChangeField}
            value={this.state.message}
          />
          <Divider className={this.props.classes.divider} />
          <IconButton
            color="primary"
            aria-label="Send"
            type="submit"
          >
            <SendIcon />
          </IconButton>
        </Paper>
      </form>
    );
  }
}

export default withStyles(messageFormStyles)(connectService(ChatContext, (state, chatService) => ({
  async sendMessage(login, message) {
    await chatService.sendMessage(login, message);
  }
}))(MessageForm));

import React, {useMemo, useState} from "react";
import path from 'path';
import {withStyles} from '@material-ui/core';
import Button from '@material-ui/core/Button';
import Dialog from '../Dialog/Dialog'
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogTitle from '@material-ui/core/DialogTitle';
import {getInstance, useService} from "global-apps-common";
import ContactChip from '../ContactChip/ContactChip';
import createChatDialogStyles from "./createChatDialogStyles";
import {withRouter} from "react-router-dom";
import Search from "../Search/Search";
import SearchContactsService from "../../modules/searchContacts/SearchContactsService";
import ContactsService from "../../modules/contacts/ContactsService";
import RoomsService from "../../modules/rooms/RoomsService";
import SelectContactsList from "../SelectContactsList/SelectContactsList";
import {withSnackbar} from "notistack";

function CreateChatDialogView({
                                classes,
                                open,
                                onClose,
                                loading,
                                onSubmit,
                                onContactToggle,
                                contacts,
                                search,
                                searchReady,
                                searchContacts,
                                onSearchChange,
                                participants,
                                publicKey
                              }) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      loading={loading}
      maxWidth='sm'
    >
      <form onSubmit={onSubmit} className={classes.form}>
        <DialogTitle>
          Add Members
        </DialogTitle>
        <DialogContent className={classes.dialogContent}>
          <div className={classes.chipsContainer}>
            {[...participants].map(([publicKey, name]) => (
              <ContactChip
                color="primary"
                label={name}
                onDelete={onContactToggle.bind(this, publicKey, name)}
              />
            ))}
          </div>
          <Search
            classes={{root: classes.search}}
            placeholder="Search people..."
            value={search}
            onChange={onSearchChange}
            searchReady={searchReady}
          />
          <SelectContactsList
            search={search}
            searchContacts={searchContacts}
            participants={participants}
            contacts={contacts}
            loading={loading}
            publicKey={publicKey}
            onContactCheck={onContactToggle}
          />
        </DialogContent>
        <DialogActions>
          <Button
            className={classes.actionButton}
            onClick={onClose}
          >
            Close
          </Button>
          <Button
            className={classes.actionButton}
            type="submit"
            color="primary"
            variant="contained"
          >
            Create
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function CreateChatDialog({classes, history, open, onClose, publicKey, enqueueSnackbar}) {
  const contactsOTStateManager = getInstance('contactsOTStateManager');
  const searchContactsService = useMemo(
    () => SearchContactsService.createFrom(contactsOTStateManager),
    [contactsOTStateManager]
  );
  const contactsService = getInstance(ContactsService);
  const roomsService = getInstance(RoomsService);

  const [loading, setLoading] = useState(false);
  const [participants, setParticipants] = useState(new Map());
  const {search, searchContacts, searchReady} = useService(searchContactsService);
  const {contacts} = useService(contactsService);

  function onSearchChange(value) {
    return searchContactsService.search(value);
  }

  const props = {
    classes,
    participants,
    loading,
    search,
    searchContacts,
    searchReady,
    contacts,
    publicKey,
    open,
    onClose,

    onSubmit(event) {
      event.preventDefault();
      setLoading(true);

      (async () => {
        if (participants.size === 0) {
          return;
        }

        const participantsKeys = participants.keys();

        for (const participantKey of participantsKeys) {
          if (!contacts.has(participantKey)) {
            await contactsService.addContact(participantKey);
          }
        }

        const roomId = await roomsService.createRoom(participantsKeys);

        history.push(path.join('/room', roomId || ''));
        setParticipants(new Map());
        onClose();
      })()
        .catch(error => enqueueSnackbar(error.message, {
          variant: 'error'
        }))
        .finally(() => {
          setLoading(false);
        });
    },

    onSearchChange(event) {
      return onSearchChange(event.target.value);
    },

    onContactToggle(participantPublicKey, name) { // TODO Remove name
      if (loading) {
        return;
      }

      const participants = new Map(props.participants);
      if (participants.has(participantPublicKey)) {
        participants.delete(participantPublicKey);
      } else {
        participants.set(participantPublicKey, name);
      }

      setParticipants(participants);
    }
  };

  return <CreateChatDialogView {...props}/>;
}

export default withRouter(
  withSnackbar(
    withStyles(createChatDialogStyles)(CreateChatDialog)
  )
);

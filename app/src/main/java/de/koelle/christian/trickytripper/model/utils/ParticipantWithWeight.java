package de.koelle.christian.trickytripper.model.utils;

import java.io.Serializable;

import de.koelle.christian.trickytripper.model.Participant;

public class ParticipantWithWeight extends Participant implements Serializable, Comparable<Participant> {

    private static final long serialVersionUID = -3692563524783113399L;
    private final Participant participant;

        public ParticipantWithWeight(Participant participant) {
        if (participant == null) throw new NullPointerException("participant must not be null");
        this.participant = participant;
    }
    public String getName() {
        return participant.getName();
    }

    public void setName(String name) {
        this.participant.setName(name);
    }

    public long getId() {
        return participant.getId();
    }

    public void setId(long id) {
        participant.setId(id);
    }

    public String getExternalId() {
        return participant.getExternalId();
    }

    public void setExternalId(String externalId) {
        participant.setExternalId(externalId);
    }

    public boolean isActive() {
        return participant.isActive();
    }

    public void setActive(boolean active) {
        participant.setActive(active);
    }

    @Override
    public String toString() {
        return "Participant [name=" + participant.getName() + ", id=" + participant.getId() + ", externalId="
                + participant.getExternalId() + ", active=" + participant.isActive() + "]";
    }

    public int compareTo(Participant another) {
        return getName().compareTo(another.getName());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (getId() ^ (getId() >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParticipantWithWeight other = (ParticipantWithWeight) obj;
        return getId() == other.getId();
    }

}

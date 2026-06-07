package app.resultatsbridge.common.model

import android.os.Parcel
import android.os.Parcelable

data class Joueur(
    val idJoueur: Int,// ID du joueur dans la table "Joueurs"
    val nom: String,
    val prenom: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(idJoueur)
        parcel.writeString(nom)
        parcel.writeString(prenom)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Joueur> {
        override fun createFromParcel(parcel: Parcel): Joueur = Joueur(parcel)
        override fun newArray(size: Int): Array<Joueur?> = arrayOfNulls(size)
    }
}

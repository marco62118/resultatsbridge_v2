package app.resultatsbridge.common.model

import android.os.Parcel
import android.os.Parcelable

data class ChangementDeMouvementEntry(
    val equipe: Equipe,
    val adversaire: Equipe,
    val tableNumero: Int,
   // val donnes: List<Int>
) : Parcelable {
    constructor(parcel: Parcel) : this(
        equipe = parcel.readParcelable(Equipe::class.java.classLoader)!!,
        adversaire = parcel.readParcelable(Equipe::class.java.classLoader)!!,
        tableNumero = parcel.readInt(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(equipe, flags)
        parcel.writeParcelable(adversaire, flags)
        parcel.writeInt(tableNumero)
       // parcel.writeList(donnes)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ChangementDeMouvementEntry> {
        override fun createFromParcel(parcel: Parcel): ChangementDeMouvementEntry =
            ChangementDeMouvementEntry(parcel)

        override fun newArray(size: Int): Array<ChangementDeMouvementEntry?> =
            arrayOfNulls(size)
    }
}
